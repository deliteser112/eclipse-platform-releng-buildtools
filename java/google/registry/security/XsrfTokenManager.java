// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.security;

import static com.google.appengine.api.users.UserServiceFactory.getUserService;
import static com.google.common.io.BaseEncoding.base64Url;
import static google.registry.model.server.ServerSecret.getServerSecret;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.users.UserService;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.hash.Hashing;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import google.registry.util.SystemClock;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Helper class for generating and validate XSRF tokens. */
public final class XsrfTokenManager {

  /** HTTP header used for transmitting XSRF tokens. */
  public static final String X_CSRF_TOKEN = "X-CSRF-Token";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @NonFinalForTesting
  private static Clock clock = new SystemClock();

  private static String encodeToken(long creationTime, String scope, String userEmail) {
    String token = Joiner.on('\t').join(getServerSecret(), userEmail, scope, creationTime);
    return base64Url().encode(Hashing.sha256()
        .newHasher(token.length())
        .putString(token, UTF_8)
        .hash()
        .asBytes());
  }

  /**
   * Generate an xsrf token for a given scope using the logged in user or else no user.
   *
   * <p>If there is no user, the entire xsrf check becomes basically a no-op, but that's ok because
   * any callback that doesn't have a user shouldn't be able to access any per-user resources
   * anyways.
   */
  public static String generateToken(String scope) {
    return generateToken(scope, getLoggedInEmailOrEmpty());
  }

  /** Generate an xsrf token for a given scope and user. */
  public static String generateToken(String scope, String email) {
    long now = clock.nowUtc().getMillis();
    return Joiner.on(':').join(encodeToken(now, scope, email), now);
  }

  private static String getLoggedInEmailOrEmpty() {
    UserService userService = getUserService();
    return userService.isUserLoggedIn() ? userService.getCurrentUser().getEmail() : "";
  }

  /** Validate an xsrf token, given the scope it was used for and an expiration duration. */
  public static boolean validateToken(String token, String scope, Duration validLifetime) {
    List<String> tokenParts = Splitter.on(':').splitToList(token);
    if (tokenParts.size() != 2) {
      logger.warningfmt("Malformed XSRF token: %s", token);
      return false;
    }
    String encodedPart = tokenParts.get(0);
    String timePart = tokenParts.get(1);
    long creationTime;
    try {
      creationTime = Long.parseLong(timePart);
    } catch (NumberFormatException e) {
      logger.warningfmt("Bad timestamp in XSRF token: %s", token);
      return false;
    }
    if (new DateTime(creationTime).plus(validLifetime).isBefore(clock.nowUtc())) {
      logger.infofmt("Expired timestamp in XSRF token: %s", token);
      return false;
    }
    String reconstructedToken = encodeToken(creationTime, scope, getLoggedInEmailOrEmpty());
    if (!reconstructedToken.equals(encodedPart)) {
      logger.warningfmt("Reconstructed XSRF mismatch: %s ≠ %s", encodedPart, reconstructedToken);
      return false;
    }
    return true;
  }

  private XsrfTokenManager() {}
}
