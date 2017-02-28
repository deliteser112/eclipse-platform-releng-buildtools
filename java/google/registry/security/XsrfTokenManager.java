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

package google.registry.security;

import static com.google.common.io.BaseEncoding.base64Url;
import static google.registry.model.server.ServerSecret.getServerSecret;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.users.UserService;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.hash.Hashing;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Helper class for generating and validate XSRF tokens. */
public final class XsrfTokenManager {

  // TODO(b/35388772): remove the scope parameter

  /** HTTP header used for transmitting XSRF tokens. */
  public static final String X_CSRF_TOKEN = "X-CSRF-Token";

  private static final Duration XSRF_VALIDITY = Duration.standardDays(1);

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final Clock clock;
  private final UserService userService;

  @Inject
  public XsrfTokenManager(Clock clock, UserService userService) {
    this.clock = clock;
    this.userService = userService;
  }

  /**
   * Encode a token.
   *
   * <p>The token is a Base64-encoded SHA-256 hash of a string containing the secret, email, scope
   * and creation time, separated by tabs. If the scope is null, the string is secret, email,
   * creation time. In the future, the scope option will be removed.
   */
  private static String encodeToken(long creationTime, @Nullable String scope, String userEmail) {
    String token =
        Joiner.on('\t').skipNulls().join(getServerSecret(), userEmail, scope, creationTime);
    return base64Url().encode(Hashing.sha256()
        .newHasher(token.length())
        .putString(token, UTF_8)
        .hash()
        .asBytes());
  }

  /**
   * Generate an xsrf token for a given scope and user.
   *
   * <p>If there is no user (email is an empty string), the entire xsrf check becomes basically a
   * no-op, but that's ok because any callback that doesn't have a user shouldn't be able to access
   * any per-user resources anyways.
   *
   * <p>The scope (or lack thereof) is passed to {@link #encodeToken}. Use of a scope in xsrf tokens
   * is deprecated; instead, use the no-argument version.
   */
  @Deprecated
  public String generateToken(@Nullable String scope, String email) {
    long now = clock.nowUtc().getMillis();
    return Joiner.on(':').join(encodeToken(now, scope, email), now);
  }

  /** Generate an xsrf token for a given user. */
  public String generateToken(String email) {
    return generateToken(null, email);
  }

  private String getLoggedInEmailOrEmpty() {
    return userService.isUserLoggedIn() ? userService.getCurrentUser().getEmail() : "";
  }

  /**
   * Validate an xsrf token, given the scope it was used for.
   *
   * <p>We plan to remove the scope parameter. As a first step, the method first checks for the
   * existence of a token with no scope. If that is not found, it then looks for the existence of a
   * token with the specified scope. Our next step will be to have clients pass in a null scope.
   * Finally, we will remove scopes from this code altogether.
   */
  @Deprecated
  public boolean validateToken(String token, @Nullable String scope) {
    return validateTokenSub(token, scope);
  }

  /**
   * Validate an xsrf token.
   *
   * <p>This is the non-scoped version to which we will transition in the future.
   */
  public boolean validateToken(String token) {
    return validateTokenSub(token, null);
  }

  private boolean validateTokenSub(String token, @Nullable String scope) {
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
    if (new DateTime(creationTime).plus(XSRF_VALIDITY).isBefore(clock.nowUtc())) {
      logger.infofmt("Expired timestamp in XSRF token: %s", token);
      return false;
    }
    // First, check for a scopeless token, because that's the token of the future.
    String reconstructedToken = encodeToken(creationTime, null, getLoggedInEmailOrEmpty());
    if (reconstructedToken.equals(encodedPart)) {
      return true;
    }

    // If we don't find one, look for one with the specified scope.
    if (scope != null) {
      reconstructedToken = encodeToken(creationTime, scope, getLoggedInEmailOrEmpty());
      if (reconstructedToken.equals(encodedPart)) {
        return true;
      }
    }

    logger.warningfmt("Reconstructed XSRF mismatch: %s ≠ %s", encodedPart, reconstructedToken);
    return false;
  }
}
