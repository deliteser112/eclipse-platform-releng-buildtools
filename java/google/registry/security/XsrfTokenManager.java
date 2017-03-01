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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.BaseEncoding.base64Url;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.api.users.UserService;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import google.registry.model.server.ServerSecret;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import java.util.List;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Helper class for generating and validate XSRF tokens. */
public final class XsrfTokenManager {

  /** HTTP header used for transmitting XSRF tokens. */
  public static final String X_CSRF_TOKEN = "X-CSRF-Token";

  /** Maximum age of an acceptable XSRF token. */
  private static final Duration XSRF_VALIDITY = Duration.standardDays(1);

  /** Token version identifier for version 1. */
  private static final String VERSION_1 = "1";

  /** Legacy scope values that will be supported during the scope removal process. */
  private static final ImmutableSet<String> LEGACY_SCOPES = ImmutableSet.of("admin", "console");

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final Clock clock;
  private final UserService userService;

  @Inject
  public XsrfTokenManager(Clock clock, UserService userService) {
    this.clock = clock;
    this.userService = userService;
  }

  /** Generates an XSRF token for a given user based on email address. */
  public String generateToken(String email) {
    checkArgumentNotNull(email);
    long timestampMillis = clock.nowUtc().getMillis();
    return encodeToken(ServerSecret.get().asBytes(), email, timestampMillis);
  }

  /**
   * Returns an XSRF token for the given server secret, user email, and timestamp.
   *
   * <p>The token format consists of three colon-delimited fields: the version number (currently 1),
   * the timestamp in milliseconds since the epoch, and the Base64url-encoded SHA-256 HMAC (using
   * the given secret key) of the user email and the timestamp millis separated by a tab character.
   *
   * <p>We use HMAC instead of a plain SHA-256 hash to avoid length-extension vulnerabilities.
   */
  private static String encodeToken(byte[] secret, String email, long timestampMillis) {
    String payload = Joiner.on('\t').skipNulls().join(email, timestampMillis);
    String hmac =
        base64Url().encode(Hashing.hmacSha256(secret).hashString(payload, UTF_8).asBytes());
    return Joiner.on(':').join(VERSION_1, timestampMillis, hmac);
  }

  /**
   * Computes the hash payload portion of a legacy-style XSRF token.
   *
   * <p>The result is a Base64-encoded SHA-256 hash of a string containing the secret, email, scope
   * and creation time, separated by tabs.
   */
  private static String computeLegacyHash(long creationTime, String scope, String userEmail) {
    checkArgument(LEGACY_SCOPES.contains(scope), "Invalid scope value: %s", scope);
    String token =
        Joiner.on('\t').join(ServerSecret.get().asUuid(), userEmail, scope, creationTime);
    return base64Url().encode(Hashing.sha256()
        .newHasher(token.length())
        .putString(token, UTF_8)
        .hash()
        .asBytes());
  }

  /**
   * Generates a legacy-style XSRF token for a given scope and user.
   *
   * <p>If there is no user (email is an empty string), the entire xsrf check becomes basically a
   * no-op, but that's ok because any callback that doesn't have a user shouldn't be able to access
   * any per-user resources anyways.
   *
   * <p>The scope is passed to {@link #computeLegacyHash}. Use of a scope in xsrf tokens is
   * deprecated; instead, use {@link #generateToken}.
   */
  // TODO(b/35388772): remove this in favor of generateToken()
  @Deprecated
  public String generateLegacyToken(String scope, String email) {
    checkArgumentNotNull(scope);
    checkArgumentNotNull(email);
    long now = clock.nowUtc().getMillis();
    return Joiner.on(':').join(computeLegacyHash(now, scope, email), now);
  }

  /**
   * Validates an XSRF token against the current logged-in user.
   *
   * This accepts both legacy-style and new-style XSRF tokens.  For legacy-style tokens, it will
   * accept tokens generated with any scope from {@link #LEGACY_SCOPES}.
   */
  public boolean validateToken(String token) {
    checkArgumentNotNull(token);
    List<String> tokenParts = Splitter.on(':').splitToList(token);
    if (tokenParts.size() < 2) {
      logger.warningfmt("Malformed XSRF token: %s", token);
      return false;
    }
    String timePart = tokenParts.get(1);
    long timestampMillis;
    try {
      timestampMillis = Long.parseLong(timePart);
    } catch (NumberFormatException e) {
      logger.warningfmt("Bad timestamp in XSRF token: %s", token);
      return false;
    }
    if (new DateTime(timestampMillis, UTC).plus(XSRF_VALIDITY).isBefore(clock.nowUtc())) {
      logger.infofmt("Expired timestamp in XSRF token: %s", token);
      return false;
    }
    String currentUserEmail =
        userService.isUserLoggedIn() ? userService.getCurrentUser().getEmail() : "";

    // Reconstruct the token to verify validity, using version 1 format if detected.
    if (tokenParts.get(0).equals(VERSION_1)) {
      String reconstructedToken =
          encodeToken(ServerSecret.get().asBytes(), currentUserEmail, timestampMillis);
      if (!token.equals(reconstructedToken)) {
        logger.warningfmt(
            "Reconstructed XSRF mismatch (got != expected): %s != %s", token, reconstructedToken);
        return false;
      }
      return true;
    } else {
      // Fall back to the legacy format, and try the few possible scopes.
      String hash = tokenParts.get(0);
      ImmutableSet.Builder<String> reconstructedTokenCandidates = new ImmutableSet.Builder<>();
      for (String scope : LEGACY_SCOPES) {
        String reconstructedHash = computeLegacyHash(timestampMillis, scope, currentUserEmail);
        reconstructedTokenCandidates.add(reconstructedHash);
        if (hash.equals(reconstructedHash)) {
          return true;
        }
      }
      logger.warningfmt(
          "Reconstructed XSRF mismatch: %s matches none of %s",
          hash, reconstructedTokenCandidates.build());
      return false;
    }
  }
}
