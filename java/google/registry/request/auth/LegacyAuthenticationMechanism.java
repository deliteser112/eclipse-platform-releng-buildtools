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

import static com.google.common.base.Strings.nullToEmpty;
import static google.registry.request.auth.AuthLevel.NONE;
import static google.registry.request.auth.AuthLevel.USER;
import static google.registry.security.XsrfTokenManager.X_CSRF_TOKEN;

import com.google.appengine.api.users.UserService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import google.registry.security.XsrfTokenManager;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/**
 * Authentication mechanism for legacy cookie-based App Engine authentication.
 *
 * <p>Just use the values returned by UserService.
 */
public class LegacyAuthenticationMechanism implements AuthenticationMechanism {

  private final UserService userService;
  private final XsrfTokenManager xsrfTokenManager;

  /** HTTP methods which are considered safe, and do not require XSRF protection. */
  private static final ImmutableSet<String> SAFE_METHODS = ImmutableSet.of("GET", "HEAD");

  @VisibleForTesting
  @Inject
  public LegacyAuthenticationMechanism(UserService userService, XsrfTokenManager xsrfTokenManager) {
    this.userService = userService;
    this.xsrfTokenManager = xsrfTokenManager;
  }

  @Override
  public AuthResult authenticate(HttpServletRequest request) {
    if (!userService.isUserLoggedIn()) {
      return AuthResult.create(NONE);
    }

    if (!SAFE_METHODS.contains(request.getMethod())
        && !xsrfTokenManager.validateToken(nullToEmpty(request.getHeader(X_CSRF_TOKEN)))) {
      return AuthResult.create(NONE);
    }

    return AuthResult.create(
        USER,
        UserAuthInfo.create(userService.getCurrentUser(), userService.isUserAdmin()));
  }
}
