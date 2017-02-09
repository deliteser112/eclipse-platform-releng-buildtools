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

import static google.registry.request.auth.AuthLevel.NONE;
import static google.registry.request.auth.AuthLevel.USER;

import com.google.appengine.api.users.UserService;
import com.google.common.annotations.VisibleForTesting;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/**
 * Authentication mechanism for legacy cookie-based App Engine authentication.
 *
 * <p>Just use the values returned by UserService.
 */
// TODO(mountford) Add XSRF protection here or elsewhere, from RequestHandler
public class LegacyAuthenticationMechanism implements AuthenticationMechanism {

  private final UserService userService;

  @VisibleForTesting
  @Inject
  public LegacyAuthenticationMechanism(UserService userService) {
    this.userService = userService;
  }

  @Override
  public AuthResult authenticate(HttpServletRequest request) {
    if (!userService.isUserLoggedIn()) {
      return AuthResult.create(NONE);
    } else {
      return AuthResult.create(
          USER,
          UserAuthInfo.create(userService.getCurrentUser(), userService.isUserAdmin()));
    }
  }
}
