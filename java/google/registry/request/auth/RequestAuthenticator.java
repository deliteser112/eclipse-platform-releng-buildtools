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

package google.registry.request.auth;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/** Top-level authentication/authorization class; calls authentication mechanisms as needed. */
public class RequestAuthenticator {

  private final AppEngineInternalAuthenticationMechanism appEngineInternalAuthenticationMechanism;
  private final ImmutableList<AuthenticationMechanism> apiAuthenticationMechanisms;
  private final LegacyAuthenticationMechanism legacyAuthenticationMechanism;

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @VisibleForTesting
  @Inject
  public RequestAuthenticator(
      AppEngineInternalAuthenticationMechanism appEngineInternalAuthenticationMechanism,
      ImmutableList<AuthenticationMechanism> apiAuthenticationMechanisms,
      LegacyAuthenticationMechanism legacyAuthenticationMechanism) {
    this.appEngineInternalAuthenticationMechanism = appEngineInternalAuthenticationMechanism;
    this.apiAuthenticationMechanisms = apiAuthenticationMechanisms;
    this.legacyAuthenticationMechanism = legacyAuthenticationMechanism;
  }

  /**
   * Attempts to authenticate and authorize the user, according to the settings of the action.
   *
   * @param auth the auth settings of the action, which determine what authentication and
   *     authorization are allowed
   * @param req the {@link HttpServletRequest}; some authentication mechanisms use HTTP headers
   * @return an authentication result if authentication/authorization was successful, or absent() if
   *     not; authentication can be "successful" even without any authentication if the action's
   *     auth settings are set to NONE -- in this case, NOT_AUTHENTICATED is returned
   */
  public Optional<AuthResult> authorize(Auth auth, HttpServletRequest req) {
    logger.infofmt("Action requires auth: %s", auth);
    AuthResult authResult = authenticate(auth, req);
    switch (auth.minimumLevel()) {
      case NONE:
        // Any authentication result is ok.
        break;
      case APP:
        if (!authResult.isAuthenticated()) {
          logger.info("Not authorized; no authentication found");
          return Optional.absent();
        }
        break;
      case USER:
        if (authResult.authLevel() != AuthLevel.USER) {
          logger.info("Not authorized; no authenticated user");
          // TODO(mountford): change this so that the caller knows to return a more helpful error
          return Optional.absent();
        }
        break;
    }
    switch (auth.userPolicy()) {
      case IGNORED:
        if (authResult.authLevel() == AuthLevel.USER) {
          logger.info("Not authorized; user policy is IGNORED, but a user was found");
          return Optional.absent();
        }
        break;
      case PUBLIC:
        // Any user auth result is okay.
        break;
      case ADMIN:
        if (authResult.userAuthInfo().isPresent()
            && !authResult.userAuthInfo().get().isUserAdmin()) {
          logger.info("Not authorized; user policy is ADMIN, but the user was not an admin");
          return Optional.absent();
        }
        break;
    }
    logger.info("Authorized");
    return Optional.of(authResult);
  }

  /**
   * Attempts to authenticate the user, according to the settings of the action.
   *
   * @param auth the auth settings of the action, which determine what authentication is allowed
   * @param req the {@link HttpServletRequest}; some authentication mechanisms use HTTP headers
   * @return an authentication result; if no authentication was made, returns NOT_AUTHENTICATED
   */
  private AuthResult authenticate(Auth auth, HttpServletRequest req) {
    checkAuthConfig(auth);
    for (Auth.AuthMethod authMethod : auth.methods()) {
      switch (authMethod) {
        // App Engine internal authentication, using the queue name header
        case INTERNAL:
          logger.info("Checking internal auth");
          // INTERNAL should be skipped if a user is required.
          if (auth.minimumLevel() != AuthLevel.USER) {
            AuthResult authResult = appEngineInternalAuthenticationMechanism.authenticate(req);
            if (authResult.isAuthenticated()) {
              logger.infofmt("Authenticated: %s", authResult);
              return authResult;
            }
          }
          break;
        // API-based user authentication mechanisms, such as OAuth
        case API:
          // checkAuthConfig will have insured that the user policy is not IGNORED.
          for (AuthenticationMechanism authMechanism : apiAuthenticationMechanisms) {
            logger.infofmt("Checking %s", authMechanism);
            AuthResult authResult = authMechanism.authenticate(req);
            if (authResult.isAuthenticated()) {
              logger.infofmt("Authenticated: %s", authResult);
              return authResult;
            }
          }
          break;
        // Legacy authentication via UserService
        case LEGACY:
          // checkAuthConfig will have insured that the user policy is not IGNORED.
          logger.info("Checking legacy auth");
          AuthResult authResult = legacyAuthenticationMechanism.authenticate(req);
          if (authResult.isAuthenticated()) {
            logger.infofmt("Authenticated: %s", authResult);
            return authResult;
          }
          break;
      }
    }
    logger.info("No authentication found");
    return AuthResult.NOT_AUTHENTICATED;
  }

  /** Validates an Auth object, checking for invalid setting combinations. */
  void checkAuthConfig(Auth auth) {
    ImmutableList<Auth.AuthMethod> authMethods = ImmutableList.copyOf(auth.methods());
    checkArgument(!authMethods.isEmpty(), "Must specify at least one auth method");
    checkArgument(
        Ordering.explicit(Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API, Auth.AuthMethod.LEGACY)
            .isStrictlyOrdered(authMethods),
        "Auth methods must be unique and strictly in order - INTERNAL, API, LEGACY");
    checkArgument(
        authMethods.contains(Auth.AuthMethod.INTERNAL),
        "Auth method INTERNAL must always be specified, and as the first auth method");
    if (authMethods.equals(ImmutableList.of(Auth.AuthMethod.INTERNAL))) {
      checkArgument(
          !auth.minimumLevel().equals(AuthLevel.USER),
          "Actions with only INTERNAL auth may not require USER auth level");
    } else {
      checkArgument(
          !auth.userPolicy().equals(Auth.UserPolicy.IGNORED),
          "Actions with auth methods beyond INTERNAL must not specify the IGNORED user policy");
    }
  }
}
