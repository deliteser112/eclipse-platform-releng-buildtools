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

package google.registry.request.auth;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.users.User;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import dagger.Lazy;
import google.registry.config.RegistryConfig.Config;
import google.registry.groups.GroupsConnection;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

/**
 * Allows access only to {@link Registrar}s the current user has access to.
 *
 * <p>A user has OWNER role on a Registrar if there exists a {@link RegistrarContact} with that
 * user's gaeId and the registrar as a parent.
 *
 * <p>An "admin" has in addition OWNER role on {@code #registryAdminClientId} and to all non-{@code
 * REAL} registrars (see {@link Registrar#getType}).
 *
 * <p>An "admin" also has ADMIN role on ALL registrars.
 *
 * <p>A user is an "admin" if they are a GAE-admin, or if their email is in the "Support" G-Suite
 * group.
 */
@Immutable
public class AuthenticatedRegistrarAccessor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The role under which access is granted. */
  public enum Role {
    OWNER,
    ADMIN
  }

  private final String userIdForLogging;

  /**
   * Gives all roles a user has for a given clientId.
   *
   * <p>The order is significant, with "more specific to this user" coming first.
   *
   * <p>Logged out users have an empty roleMap.
   */
  private final ImmutableSetMultimap<String, Role> roleMap;

  /**
   * Overriding the injected {@link GroupsConnection} for tests.
   *
   * <p>{@link GroupsConnection} needs the injected DelegatedCredential GoogleCredential. However,
   * this can't be initialized in the test environment.
   *
   * <p>The test server used in the javatests/google/registry/webdriver/ tests will hang if we try
   * to instantiate the {@link GroupsConnection}. So instead we inject a {@link Lazy} version and
   * allow tests to override the injected instace with (presumabley) a mock insteance.
   */
  @VisibleForTesting public static GroupsConnection overrideGroupsConnection = null;

  @Inject
  public AuthenticatedRegistrarAccessor(
      AuthResult authResult,
      @Config("registryAdminClientId") String registryAdminClientId,
      @Config("gSuiteSupportGroupEmailAddress") String gSuiteSupportGroupEmailAddress,
      Lazy<GroupsConnection> groupsConnection) {
    this(
        authResult,
        registryAdminClientId,
        gSuiteSupportGroupEmailAddress,
        overrideGroupsConnection != null ? overrideGroupsConnection : groupsConnection.get());
  }

  @VisibleForTesting
  AuthenticatedRegistrarAccessor(
      AuthResult authResult,
      String registryAdminClientId,
      String gSuiteSupportGroupEmailAddress,
      GroupsConnection groupsConnection) {
    this(
        authResult.userIdForLogging(),
        createRoleMap(
            authResult, registryAdminClientId, groupsConnection, gSuiteSupportGroupEmailAddress));

    logger.atInfo().log(
        "%s has the following roles: %s", authResult.userIdForLogging(), roleMap);
  }

  private AuthenticatedRegistrarAccessor(
      String userIdForLogging, ImmutableSetMultimap<String, Role> roleMap) {
    this.userIdForLogging = checkNotNull(userIdForLogging);
    this.roleMap = checkNotNull(roleMap);
  }

  /**
   * Creates a "logged-in user" accessor with a given role map, used for tests.
   *
   * <p>The user's "name" in logs and exception messages is "TestUserId".
   */
  @VisibleForTesting
  public static AuthenticatedRegistrarAccessor createForTesting(
      ImmutableSetMultimap<String, Role> roleMap) {
    return new AuthenticatedRegistrarAccessor("TestUserId", roleMap);
  }

  /**
   * A map that gives all roles a user has for a given clientId.
   *
   * <p>Throws a {@link RegistrarAccessDeniedException} if the user is not logged in.
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
   * Returns all the roles the current user has on the given registrar.
   *
   * <p>This is syntactic sugar for {@code getAllClientIdWithRoles().get(clientId)}.
   */
  public ImmutableSet<Role> getRolesForRegistrar(String clientId) {
    return getAllClientIdWithRoles().get(clientId);
  }

  /**
   * Checks if we have a given role for a given registrar.
   *
   * <p>This is syntactic sugar for {@code getAllClientIdWithRoles().containsEntry(clientId, role)}.
   */
  public boolean hasRoleOnRegistrar(Role role, String clientId) {
    return getAllClientIdWithRoles().containsEntry(clientId, role);
  }

  /**
   * "Guesses" which client ID the user wants from all those they have access to.
   *
   * <p>If no such ClientIds exist, throws a RegistrarAccessDeniedException.
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
  public String guessClientId() throws RegistrarAccessDeniedException {
    return getAllClientIdWithRoles().keySet().stream()
        .findFirst()
        .orElseThrow(
            () ->
                new RegistrarAccessDeniedException(
                    String.format("%s isn't associated with any registrar", userIdForLogging)));
  }

  /**
   * Loads a Registrar IFF the user is authorized.
   *
   * <p>Throws a {@link RegistrarAccessDeniedException} if the user is not logged in, or not
   * authorized to access the requested registrar.
   *
   * @param clientId ID of the registrar we request
   */
  public Registrar getRegistrar(String clientId) throws RegistrarAccessDeniedException {
    verifyAccess(clientId);

    Registrar registrar =
        Registrar.loadByClientId(clientId)
            .orElseThrow(
                () ->
                    new RegistrarAccessDeniedException(
                        String.format("Registrar %s not found", clientId)));

    if (!clientId.equals(registrar.getClientId())) {
      logger.atSevere().log(
          "registrarLoader.apply(clientId) returned a Registrar with a different clientId. "
              + "Requested: %s, returned: %s.",
          clientId, registrar.getClientId());
      throw new RegistrarAccessDeniedException("Internal error - please check logs");
    }

    return registrar;
  }

  public void verifyAccess(String clientId) throws RegistrarAccessDeniedException {
    ImmutableSet<Role> roles = getAllClientIdWithRoles().get(clientId);

    if (roles.isEmpty()) {
      throw new RegistrarAccessDeniedException(
          String.format("%s doesn't have access to registrar %s", userIdForLogging, clientId));
    }
    logger.atInfo().log("%s has %s access to registrar %s.", userIdForLogging, roles, clientId);
  }

  public String userIdForLogging() {
    return userIdForLogging;
  }

  @Override
  public String toString() {
    return toStringHelper(getClass()).add("user", userIdForLogging).toString();
  }

  private static boolean checkIsSupport(
      GroupsConnection groupsConnection, String userEmail, String supportEmail) {
    if (Strings.isNullOrEmpty(supportEmail)) {
      return false;
    }
    try {
      return groupsConnection.isMemberOfGroup(userEmail, supportEmail);
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log(
          "Error checking whether email %s belongs to support group %s."
              + " Skipping support role check",
          userEmail, supportEmail);
      return false;
    }
  }

  private static ImmutableSetMultimap<String, Role> createRoleMap(
      AuthResult authResult,
      String registryAdminClientId,
      GroupsConnection groupsConnection,
      String gSuiteSupportGroupEmailAddress) {

    if (!authResult.userAuthInfo().isPresent()) {
      return ImmutableSetMultimap.of();
    }

    UserAuthInfo userAuthInfo = authResult.userAuthInfo().get();

    User user = userAuthInfo.user();
    boolean isAdmin = userAuthInfo.isUserAdmin();
    boolean isSupport =
        checkIsSupport(groupsConnection, user.getEmail(), gSuiteSupportGroupEmailAddress);

    ImmutableSetMultimap.Builder<String, Role> builder = new ImmutableSetMultimap.Builder<>();

    ofy()
        .load()
        .type(RegistrarContact.class)
        .filter("gaeUserId", user.getUserId())
        .forEach(contact -> builder.put(contact.getParent().getName(), Role.OWNER));
    if (isAdmin && !Strings.isNullOrEmpty(registryAdminClientId)) {
      builder.put(registryAdminClientId, Role.OWNER);
    }

    if (isAdmin || isSupport) {
      // Admins and support have ADMIN access to all registrars, and OWNER access to all non-REAL
      // registrars
      ofy()
          .load()
          .type(Registrar.class)
          .forEach(registrar -> {
            if (!Registrar.Type.REAL.equals(registrar.getType())) {
              builder.put(registrar.getClientId(), Role.OWNER);
            }
            builder.put(registrar.getClientId(), Role.ADMIN);
          });
    }

    return builder.build();
  }

  /** Exception thrown when the current user doesn't have access to the requested Registrar. */
  public static class RegistrarAccessDeniedException extends Exception {
    RegistrarAccessDeniedException(String message) {
      super(message);
    }
  }
}
