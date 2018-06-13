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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PreconditionsUtils.checkArgumentPresent;

import com.google.appengine.api.users.User;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.googlecode.objectify.Key;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.request.HttpException.ForbiddenException;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** HTTP session management helper class. */
@Immutable
public class SessionUtils {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CLIENT_ID_ATTRIBUTE = "clientId";

  @Inject
  @Config("registryAdminClientId")
  String registryAdminClientId;

  @Inject
  public SessionUtils() {}

  /**
   * Checks that the authentication result indicates a user that has access to the registrar
   * console, then gets the associated registrar.
   *
   * <p>Throws a {@link ForbiddenException} if the user is not logged in, or not authorized to use
   * the registrar console.
   */
  @CheckReturnValue
  Registrar getRegistrarForAuthResult(HttpServletRequest request, AuthResult authResult) {
    if (!authResult.userAuthInfo().isPresent()) {
      throw new ForbiddenException("Not logged in");
    }
    if (!checkRegistrarConsoleLogin(request, authResult.userAuthInfo().get())) {
      throw new ForbiddenException("Not authorized to access Registrar Console");
    }
    String clientId = getRegistrarClientId(request);
    return checkArgumentPresent(
        Registrar.loadByClientId(clientId),
        "Registrar %s not found",
        clientId);
  }

  /**
   * Checks that the specified user has access to the Registrar Console.
   *
   * <p>This routine will first check the HTTP session (creating one if it doesn't exist) for the
   * {@code clientId} attribute:
   *
   * <ul>
   *   <li>If it does not exist, then we will attempt to guess the {@link Registrar} with which the
   *       user is associated. The {@code clientId} of the first matching {@code Registrar} will
   *       then be stored to the HTTP session.
   *   <li>If it does exist, then we'll fetch the Registrar from Datastore to make sure access
   *       wasn't revoked.
   * </ul>
   *
   * <p><b>Note:</b> You must ensure the user has logged in before calling this method.
   *
   * @return {@code false} if user does not have access, in which case the caller should write an
   *     error response and abort the request.
   */
  @CheckReturnValue
  public boolean checkRegistrarConsoleLogin(HttpServletRequest req, UserAuthInfo userAuthInfo) {
    checkState(userAuthInfo != null, "No logged in user found");
    User user = userAuthInfo.user();
    HttpSession session = req.getSession();
    String clientId = (String) session.getAttribute(CLIENT_ID_ATTRIBUTE);

    // Use the clientId if it exists
    if (clientId != null) {
      if (!hasAccessToRegistrar(clientId, user.getUserId(), userAuthInfo.isUserAdmin())) {
        logger.atInfo().log("Registrar Console access revoked: %s", clientId);
        session.invalidate();
        return false;
      }
      logger.atInfo().log(
          "Associating user %s with given registrar %s.", user.getUserId(), clientId);
      return true;
    }

    // The clientId was null, so let's try and find a registrar this user is associated with
    Optional<Registrar> registrar = findRegistrarForUser(user.getUserId());
    if (registrar.isPresent()) {
      verify(isInAllowedContacts(registrar.get(), user.getUserId()));
      logger.atInfo().log(
          "Associating user %s with found registrar %s.",
          user.getUserId(), registrar.get().getClientId());
      session.setAttribute(CLIENT_ID_ATTRIBUTE, registrar.get().getClientId());
      return true;
    }

    // We couldn't guess the registrar, but maybe the user is an admin and we can use the
    // registryAdminClientId
    if (userAuthInfo.isUserAdmin()) {
      if (Strings.isNullOrEmpty(registryAdminClientId)) {
        logger.atInfo().log(
            "Cannot associate admin user %s with configured client Id."
                + " ClientId is null or empty.",
            user.getUserId());
        return false;
      }
      if (!Registrar.loadByClientIdCached(registryAdminClientId).isPresent()) {
        logger.atInfo().log(
            "Cannot associate admin user %s with configured client Id %s."
                + " Registrar does not exist.",
            user.getUserId(), registryAdminClientId);
        return false;
      }
      logger.atInfo().log(
          "User %s is an admin with no associated registrar."
              + " Automatically associating the user with configured client Id %s.",
          user.getUserId(), registryAdminClientId);
      session.setAttribute(CLIENT_ID_ATTRIBUTE, registryAdminClientId);
      return true;
    }

    // We couldn't find any relevant clientId
    logger.atInfo().log("User not associated with any Registrar: %s", user.getUserId());
    return false;
  }

  /**
   * Returns {@link Registrar} clientId associated with HTTP session.
   *
   * @throws IllegalStateException if you forgot to call {@link #checkRegistrarConsoleLogin}.
   */
  @CheckReturnValue
  public String getRegistrarClientId(HttpServletRequest req) {
    String clientId = (String) req.getSession().getAttribute(CLIENT_ID_ATTRIBUTE);
    checkState(clientId != null, "You forgot to call checkRegistrarConsoleLogin()");
    return clientId;
  }

  /** Returns first {@link Registrar} that {@code gaeUserId} is authorized to administer. */
  private static Optional<Registrar> findRegistrarForUser(String gaeUserId) {
    RegistrarContact contact =
        ofy().load().type(RegistrarContact.class).filter("gaeUserId", gaeUserId).first().now();
    if (contact == null) {
      return Optional.empty();
    }
    String registrarClientId = contact.getParent().getName();
    Optional<Registrar> result = Registrar.loadByClientIdCached(registrarClientId);
    if (!result.isPresent()) {
      logger.atSevere().log(
          "A contact record exists for non-existent registrar: %s.", Key.create(contact));
    }
    return result;
  }

  /** @see #isInAllowedContacts(Registrar, String) */
  boolean hasAccessToRegistrar(String clientId, String gaeUserId, boolean isAdmin) {
    Optional<Registrar> registrar = Registrar.loadByClientIdCached(clientId);
    if (!registrar.isPresent()) {
      logger.atWarning().log("Registrar '%s' disappeared from Datastore!", clientId);
      return false;
    }
    if (isAdmin && clientId.equals(registryAdminClientId)) {
      return true;
    }
    return isInAllowedContacts(registrar.get(), gaeUserId);
  }

  /**
   * Returns {@code true} if {@code gaeUserId} is listed in contacts with access to the registrar.
   *
   * <p>Each registrar contact can either have getGaeUserId equals null or the user's gaeUserId.
   * Null means the contact doesn't have access to the registrar console. None-null means the
   * contact has access.
   */
  private static boolean isInAllowedContacts(Registrar registrar, final String gaeUserId) {
    return registrar
        .getContacts()
        .stream()
        .anyMatch(contact -> gaeUserId.equals(contact.getGaeUserId()));
  }
}
