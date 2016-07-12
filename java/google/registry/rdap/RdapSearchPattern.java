// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.rdap;

import google.registry.request.HttpException.UnprocessableEntityException;
import javax.annotation.Nullable;

/**
 * Object containing the results of parsing an RDAP partial match search pattern. Search patterns
 * are of the form XXXX, XXXX* or XXXX*.YYYY. There can be at most one wildcard character, and it
 * must be at the end, except for a possible suffix string on the end to restrict the search to a
 * particular TLD (for domains) or domain (for nameservers).
 *
 * @see <a href="http://www.ietf.org/rfc/rfc7482.txt">
 *        RFC 7482: Registration Data Access Protocol (RDAP) Query Format</a>
 */
public final class RdapSearchPattern {

  /** String before the wildcard character. */
  private final String initialString;

  /** If false, initialString contains the entire search string. */
  private final boolean hasWildcard;

  /**
   * Terminating suffix after the wildcard, or null if none was specified; for domains, it should be
   * a TLD, for nameservers, a domain. RFC 7482 requires only that it be a sequence of domain
   * labels, but this definition is stricter for efficiency purposes.
   */
  @Nullable
  private final String suffix;

  private RdapSearchPattern(
      final String initialString, final boolean hasWildcard, @Nullable final String suffix) {
    this.initialString = initialString;
    this.hasWildcard = hasWildcard;
    this.suffix = suffix;
  }

  public String getInitialString() {
    return initialString;
  }

  public boolean getHasWildcard() {
    return hasWildcard;
  }

  @Nullable
  public String getSuffix() {
    return suffix;
  }

  /**
   * Attempts to return the next string in sort order after {@link #getInitialString()
   * initialString}. This can be used to convert a wildcard query into a range query, by looking for
   * strings greater than or equal to {@link #getInitialString() initialString} and less than {@link
   * #getNextInitialString() nextInitialString}.
   */
  public String getNextInitialString() {
    return initialString.substring(0, initialString.length() - 1)
        + (char) (initialString.charAt(initialString.length() - 1) + 1);
  }

  /**
   * Creates a SearchPattern using the provided search pattern string.
   *
   * @param pattern the string containing the partial match pattern
   * @param allowSuffix true if a suffix is allowed after the wildcard
   *
   * @throws UnprocessableEntityException if {@code pattern} does not meet the requirements of RFC
   *    7482
   */
  public static RdapSearchPattern create(
      String pattern, boolean allowSuffix) throws UnprocessableEntityException {
    String initialString;
    boolean hasWildcard;
    String suffix;
    // If there's no wildcard character, just lump everything into the initial string.
    int wildcardPos = pattern.indexOf('*');
    if (wildcardPos < 0) {
      initialString = pattern;
      hasWildcard = false;
      suffix = null;
    } else if (pattern.indexOf('*', wildcardPos + 1) >= 0) {
      throw new UnprocessableEntityException("Only one wildcard allowed");
    } else {
      hasWildcard = true;
      // Check for a suffix (e.g. exam*.com or ns*.example.com).
      if (pattern.length() > wildcardPos + 1) {
        if (!allowSuffix) {
          throw new UnprocessableEntityException("Suffix not allowed after wildcard");
        }
        if ((pattern.length() == wildcardPos + 2) || (pattern.charAt(wildcardPos + 1) != '.')) {
          throw new UnprocessableEntityException(
              "Suffix after wildcard must be one or more domain"
                  + " name labels, e.g. exam*.tld, ns*.example.tld");
        }
        suffix = pattern.substring(wildcardPos + 2);
      } else {
        suffix = null;
      }
      initialString = pattern.substring(0, wildcardPos);
    }
    if (initialString.length() < 2) {
      throw new UnprocessableEntityException("At least two characters must be specified");
    }
    if (initialString.startsWith("xn--")
        && (initialString.length() < 7)) {
      throw new UnprocessableEntityException(
          "At least seven characters must be specified for punycode domain searches");
    }
    return new RdapSearchPattern(initialString, hasWildcard, suffix);
  }

  /**
   * Checks a string to make sure that it matches the search pattern.
   *
   * @param string the string to be matched
   * @return true if the pattern matches the string
   */
  public boolean matches(@Nullable String string) {
    if (string == null) {
      return false;
    }
    int lengthAccountedFor = 0;
    if (initialString != null) {
      if (!string.startsWith(initialString)) {
        return false;
      }
      lengthAccountedFor += initialString.length();
    }
    if (suffix != null) {
      if (!string.endsWith(suffix)) {
        return false;
      }
      lengthAccountedFor += suffix.length();
    }
    return hasWildcard
        ? (lengthAccountedFor <= string.length())
        : (lengthAccountedFor == string.length());
  }
}
