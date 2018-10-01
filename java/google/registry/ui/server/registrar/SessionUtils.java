// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

/** Authenticated Registrar access helper class. */
@Immutable
public class SessionUtils {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  @Config("registryAdminClientId")
  String registryAdminClientId;

  @Inject
  public SessionUtils() {}

  /**
   * Loads Registrar on behalf of an authorised user.
   *
   * <p>Throws a {@link ForbiddenException} if the user is not logged in, or not authorized to
   * access the requested registrar.
   *
   * @param clientId ID of the registrar we request
   * @param authResult AuthResult of the user on behalf of which we want to access the data
   */
  public Registrar getRegistrarForUser(String clientId, AuthResult authResult) {
    return getAndAuthorize(Registrar::loadByClientId, clientId, authResult);
  }

  /**
   * Loads a Registrar from the cache on behalf of an authorised user.
   *
   * <p>Throws a {@link ForbiddenException} if the user is not logged in, or not authorized to
   * access the requested registrar.
   *
   * @param clientId ID of the registrar we request
   * @param authResult AuthResult of the user on behalf of which we want to access the data
   */
  public Registrar getRegistrarForUserCached(String clientId, AuthResult authResult) {
    return getAndAuthorize(Registrar::loadByClientIdCached, clientId, authResult);
  }

  Registrar getAndAuthorize(
      Function<String, Optional<Registrar>> registrarLoader,
      String clientId,
      AuthResult authResult) {
    UserAuthInfo userAuthInfo =
        authResult.userAuthInfo().orElseThrow(() -> new ForbiddenException("Not logged in"));
    boolean isAdmin = userAuthInfo.isUserAdmin();
    User user = userAuthInfo.user();
    String userIdForLogging = authResult.userIdForLogging();

    Registrar registrar =
        registrarLoader
            .apply(clientId)
            .orElseThrow(
                () -> new ForbiddenException(String.format("Registrar %s not found", clientId)));

    if (isInAllowedContacts(registrar, user)) {
      logger.atInfo().log("User %s has access to registrar %s.", userIdForLogging, clientId);
      return registrar;
    }

    if (isAdmin && clientId.equals(registryAdminClientId)) {
      // Admins have access to the registryAdminClientId even if they aren't explicitly in the
      // allowed contacts
      logger.atInfo().log("Allowing admin %s access to registrar %s.", userIdForLogging, clientId);
      return registrar;
    }

    throw new ForbiddenException(
        String.format("User %s doesn't have access to registrar %s", userIdForLogging, clientId));
  }

  /**
   * Tries to guess the {@link Registrar} with which the user is associated.
   *
   * <p>Returns the {@code clientId} of a {@link Registrar} the user has access to (is on the
   * contact list). If the user has access to multiple {@link Registrar}s, an arbitrary one is
   * selected. If the user is an admin without access to any {@link Registrar}s, {@link
   * #registryAdminClientId} is returned if it is defined.
   *
   * <p>If no {@code clientId} is found, throws a {@link ForbiddenException}.
   *
   * <p>If you want to load the {@link Registrar} object from this (or any other) {@code clientId},
   * in order to perform actions on behalf of a user, you must use {@link #getRegistrarForUser}
   * which makes sure the user has permissions.
   *
   * <p>Note that this is an OPTIONAL step in the authentication - only used if we don't have any
   * other clue as to the requested {@code clientId}. It is perfectly OK to get a {@code clientId}
   * from any other source, as long as the registrar is then loaded using {@link
   * #getRegistrarForUser}.
   */
  public String guessClientIdForUser(AuthResult authResult) {

    UserAuthInfo userAuthInfo =
        authResult.userAuthInfo().orElseThrow(() -> new ForbiddenException("No logged in"));
    boolean isAdmin = userAuthInfo.isUserAdmin();
    User user = userAuthInfo.user();
    String userIdForLogging = authResult.userIdForLogging();

    RegistrarContact contact =
        ofy()
            .load()
            .type(RegistrarContact.class)
            .filter("gaeUserId", user.getUserId())
            .first()
            .now();
    if (contact != null) {
      String registrarClientId = contact.getParent().getName();
      logger.atInfo().log(
          "Associating user %s with found registrar %s.", userIdForLogging, registrarClientId);
      return registrarClientId;
    }

    // We couldn't find the registrar, but maybe the user is an admin and we can use the
    // registryAdminClientId
    if (isAdmin) {
      if (!Strings.isNullOrEmpty(registryAdminClientId)) {
        logger.atInfo().log(
            "User %s is an admin with no associated registrar."
                + " Automatically associating the user with configured client Id %s.",
            userIdForLogging, registryAdminClientId);
        return registryAdminClientId;
      }
      logger.atInfo().log(
          "Cannot associate admin user %s with configured client Id."
              + " ClientId is null or empty.",
          userIdForLogging);
    }

    // We couldn't find any relevant clientId
    throw new ForbiddenException(
        String.format("User %s isn't associated with any registrar", userIdForLogging));
  }

  /**
   * Returns {@code true} if {@code user} is listed in contacts with access to the registrar.
   *
   * <p>Each registrar contact can either have getGaeUserId equals null or the user's gaeUserId.
   * Null means the contact doesn't have access to the registrar console. None-null means the
   * contact has access.
   */
  private static boolean isInAllowedContacts(Registrar registrar, User user) {
    String gaeUserId = user.getUserId();
    return registrar
        .getContacts()
        .stream()
        .anyMatch(contact -> gaeUserId.equals(contact.getGaeUserId()));
  }
}
