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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.request.HttpException.ForbiddenException;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

/**
 * Allows access only to {@link Registrar}s the current user has access to.
 *
 * <p>A user has OWNER role on a Registrar if there exists a {@link RegistrarContact} with
 * that user's gaeId and the registrar as a parent.
 *
 * <p>An admin has in addition OWNER role on {@link #registryAdminClientId}.
 *
 * <p>An admin also has ADMIN role on ALL registrars.
 */
@Immutable
public class AuthenticatedRegistrarAccessor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The role under which access is granted. */
  public enum Role {
    OWNER,
    ADMIN
  }

  AuthResult authResult;
  String registryAdminClientId;

  /**
   * Gives all roles a user has for a given clientId.
   *
   * <p>The order is significant, with "more specific to this user" coming first.
   */
  private final ImmutableSetMultimap<String, Role> roleMap;

  @Inject
  public AuthenticatedRegistrarAccessor(
      AuthResult authResult, @Config("registryAdminClientId") String registryAdminClientId) {
    this.authResult = authResult;
    this.registryAdminClientId = registryAdminClientId;
    this.roleMap = createRoleMap(authResult, registryAdminClientId);

    logger.atInfo().log(
        "%s has the following roles: %s", authResult.userIdForLogging(), roleMap);
  }

  /**
   * A map that gives all roles a user has for a given clientId.
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
  public ImmutableSetMultimap<String, Role> getAllClientIdWithRoles() {
    return roleMap;
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
    return getAllClientIdWithRoles().keySet().stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ForbiddenException(
                    String.format(
                        "%s isn't associated with any registrar",
                        authResult.userIdForLogging())));
  }

  /**
   * Loads a Registrar IFF the user is authorized.
   *
   * <p>Throws a {@link ForbiddenException} if the user is not logged in, or not authorized to
   * access the requested registrar.
   *
   * @param clientId ID of the registrar we request
   */
  public Registrar getRegistrar(String clientId) {
    verifyLoggedIn();

    ImmutableSet<Role> roles = getAllClientIdWithRoles().get(clientId);

    if (roles.isEmpty()) {
      throw new ForbiddenException(
          String.format(
              "%s doesn't have access to registrar %s",
              authResult.userIdForLogging(), clientId));
    }

    Registrar registrar =
        Registrar.loadByClientId(clientId)
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
        "%s has %s access to registrar %s.", authResult.userIdForLogging(), roles, clientId);
    return registrar;
  }

  private static ImmutableSetMultimap<String, Role> createRoleMap(
      AuthResult authResult, String registryAdminClientId) {

    if (!authResult.userAuthInfo().isPresent()) {
      return ImmutableSetMultimap.of();
    }

    UserAuthInfo userAuthInfo = authResult.userAuthInfo().get();

    boolean isAdmin = userAuthInfo.isUserAdmin();
    User user = userAuthInfo.user();

    ImmutableSetMultimap.Builder<String, Role> builder = new ImmutableSetMultimap.Builder<>();

    ofy()
        .load()
        .type(RegistrarContact.class)
        .filter("gaeUserId", user.getUserId())
        .forEach(
            contact ->
                builder
                    .put(contact.getParent().getName(), Role.OWNER));
    if (isAdmin && !Strings.isNullOrEmpty(registryAdminClientId)) {
      builder
          .put(registryAdminClientId, Role.OWNER);
    }

    if (isAdmin) {
      // Admins have access to all registrars
      ofy()
          .load()
          .type(Registrar.class)
          .forEach(registrar -> builder.put(registrar.getClientId(), Role.ADMIN));
    }

    return builder.build();
  }

  private void verifyLoggedIn() {
    if (!authResult.userAuthInfo().isPresent()) {
      throw new ForbiddenException("Not logged in");
    }
  }
}
