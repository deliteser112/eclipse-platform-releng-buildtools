// Copyright 2018 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.ui.server.registrar;

import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.users.User;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.request.HttpException.ForbiddenException;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

/**
 * Allows access only to {@link Registrar}s the current user has access to.
 *
 * <p>A user has read+write access to a Registrar if there exists a {@link RegistrarContact} with
 * that user's gaeId and the registrar as a parent.
 *
 * <p>An admin has in addition read+write access to {@link #registryAdminClientId}.
 *
 * <p>An admin also has read access to ALL registrars.
 */
@Immutable
public class AuthenticatedRegistrarAccessor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Type of access we're requesting. */
  public enum AccessType {
    READ,
    UPDATE
  }

  AuthResult authResult;
  String registryAdminClientId;

  /**
   * For any AccessType requested - all clientIDs this user is allowed that AccessType on.
   *
   * <p>The order is significant, with "more specific to this user" coming first.
   */
  private final ImmutableSetMultimap<AccessType, String> accessMap;

  @Inject
  public AuthenticatedRegistrarAccessor(
      AuthResult authResult, @Config("registryAdminClientId") String registryAdminClientId) {
    this.authResult = authResult;
    this.registryAdminClientId = registryAdminClientId;
    this.accessMap = createAccessMap(authResult, registryAdminClientId);

    logger.atInfo().log(
        "User %s has the following accesses: %s", authResult.userIdForLogging(), accessMap);
  }

  /**
   * Loads a Registrar IFF the user is authorized.
   *
   * <p>Throws a {@link ForbiddenException} if the user is not logged in, or not authorized to
   * access the requested registrar.
   *
   * @param clientId ID of the registrar we request
   * @param accessType what kind of access do we want for this registrar - just read it or write as
   *     well? (different users might have different access levels)
   */
  public Registrar getRegistrar(String clientId, AccessType accessType) {
    return getAndAuthorize(Registrar::loadByClientId, clientId, accessType);
  }

  /**
   * Loads a Registrar from the cache IFF the user is authorized.
   *
   * <p>Throws a {@link ForbiddenException} if the user is not logged in, or not authorized to
   * access the requested registrar.
   *
   * @param clientId ID of the registrar we request
   * @param accessType what kind of access do we want for this registrar - just read it or write as
   *     well? (different users might have different access levels)
   */
  public Registrar getRegistrarForUserCached(String clientId, AccessType accessType) {
    return getAndAuthorize(Registrar::loadByClientIdCached, clientId, accessType);
  }

  /**
   * For all {@link AccessType}s, Returns all ClientIds this user is allowed this access.
   *
   * <p>Throws a {@link ForbiddenException} if the user is not logged in.
   *
   * <p>The result is ordered starting from "most specific to this user".
   *
   * <p>If you want to load the {@link Registrar} object from these (or any other) {@code clientId},
   * in order to perform actions on behalf of a user, you must use {@link #getRegistrar} which makes
   * sure the user has permissions.
   *
   * <p>Note that this is an OPTIONAL step in the authentication - only used if we don't have any
   * other clue as to the requested {@code clientId}. It is perfectly OK to get a {@code clientId}
   * from any other source, as long as the registrar is then loaded using {@link #getRegistrar}.
   */
  public ImmutableSetMultimap<AccessType, String> getAllClientIdWithAccess() {
    verifyLoggedIn();
    return accessMap;
  }

  /**
   * "Guesses" which client ID the user wants from all those they have access to.
   *
   * <p>If no such ClientIds exist, throws a ForbiddenException.
   *
   * <p>This should be the ClientId "most likely wanted by the user".
   *
   * <p>If you want to load the {@link Registrar} object from this (or any other) {@code clientId},
   * in order to perform actions on behalf of a user, you must use {@link #getRegistrar} which makes
   * sure the user has permissions.
   *
   * <p>Note that this is an OPTIONAL step in the authentication - only used if we don't have any
   * other clue as to the requested {@code clientId}. It is perfectly OK to get a {@code clientId}
   * from any other source, as long as the registrar is then loaded using {@link #getRegistrar}.
   */
  public String guessClientId() {
    verifyLoggedIn();
    return getAllClientIdWithAccess().values().stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ForbiddenException(
                    String.format(
                        "User %s isn't associated with any registrar",
                        authResult.userIdForLogging())));
  }

  private Registrar getAndAuthorize(
      Function<String, Optional<Registrar>> registrarLoader,
      String clientId,
      AccessType accessType) {
    verifyLoggedIn();

    if (!accessMap.containsEntry(accessType, clientId)) {
      throw new ForbiddenException(
          String.format(
              "User %s doesn't have %s access to registrar %s",
              authResult.userIdForLogging(), accessType, clientId));
    }

    Registrar registrar =
        registrarLoader
            .apply(clientId)
            .orElseThrow(
                () -> new ForbiddenException(String.format("Registrar %s not found", clientId)));

    if (!clientId.equals(registrar.getClientId())) {
      logger.atSevere().log(
          "registrarLoader.apply(clientId) returned a Registrar with a different clientId. "
              + "Requested: %s, returned: %s.",
          clientId, registrar.getClientId());
      throw new ForbiddenException("Internal error - please check logs");
    }

    logger.atInfo().log(
        "User %s has %s access to registrar %s.",
        authResult.userIdForLogging(), accessType, clientId);
    return registrar;
  }

  private static ImmutableSetMultimap<AccessType, String> createAccessMap(
      AuthResult authResult, String registryAdminClientId) {

    if (!authResult.userAuthInfo().isPresent()) {
      return ImmutableSetMultimap.of();
    }

    UserAuthInfo userAuthInfo = authResult.userAuthInfo().get();

    boolean isAdmin = userAuthInfo.isUserAdmin();
    User user = userAuthInfo.user();

    ImmutableSetMultimap.Builder<AccessType, String> builder = new ImmutableSetMultimap.Builder<>();

    ofy()
        .load()
        .type(RegistrarContact.class)
        .filter("gaeUserId", user.getUserId())
        .forEach(
            contact ->
                builder
                    .put(AccessType.UPDATE, contact.getParent().getName())
                    .put(AccessType.READ, contact.getParent().getName()));
    if (isAdmin && !Strings.isNullOrEmpty(registryAdminClientId)) {
      logger.atInfo().log(
          "Giving admin %s read+write access to admin registrar %s",
          authResult.userIdForLogging(), registryAdminClientId);
      builder
          .put(AccessType.UPDATE, registryAdminClientId)
          .put(AccessType.READ, registryAdminClientId);
    }

    if (isAdmin) {
      // Admins have READ access to all registrars
      logger.atInfo().log(
          "Giving admin %s read-only access to all registrars", authResult.userIdForLogging());
      ofy()
          .load()
          .type(Registrar.class)
          .forEach(registrar -> builder.put(AccessType.READ, registrar.getClientId()));
    }

    return builder.build();
  }

  private void verifyLoggedIn() {
    if (!authResult.userAuthInfo().isPresent()) {
      throw new ForbiddenException("Not logged in");
    }
  }
}
