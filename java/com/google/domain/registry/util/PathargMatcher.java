// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.util;

import java.util.regex.Pattern;

/** Helper for extracting pathargs from the servlet request pathinfo with colon notation. */
public final class PathargMatcher {

  private static final Pattern PATHARG_MATCHER = Pattern.compile(":(?<name>[^/]+)");

  /**
   * Returns regex pattern for parsing an HTTP path using Java 7 named groups.
   *
   * <p>The named groups should use colon notation. For example:<pre>   {@code
   *
   *   Pattern pattern = PathargMatcher.forPath("/task/:tld/:registrar");
   *   Matcher matcher = pattern.matcher("/task/soy/TheRegistrar");
   *   assertTrue(matcher.matches());
   *   assertEquals("soy", matcher.group("soy"));
   *   assertEquals("TheRegistrar", matcher.group("registrar"));}</pre>
   *
   * <h3>Best Practices</h3>
   *
   * <p>Pathargs is a good choice for parameters that:
   *
   * <ol>
   * <li>Are mandatory
   * <li>Never contain slashes
   * <li>Part of the resource name
   * <li>About 1-10 characters in length
   * <li>Matches the pattern {@code [-._a-zA-Z0-9]+} (not enforced)
   * </ol>
   *
   * <p>Otherwise you may want to consider using normal parameters.
   *
   * <h3>Trailing Slash</h3>
   *
   * <p>This class does not allow you to make the trailing slash in the path optional. You must
   * either forbid it (recommended) or require it (not recommended). If you wish to make the
   * trailing slash optional, you should configure your frontend webserver to 30x redirect to the
   * canonical path.
   *
   * <p>There's no consensus in the web development community about whether or not the canonical
   * path should include or omit the trailing slash. The Django community prefers to keep the
   * trailing slash; whereas the Ruby on Rails community has traditionally omitted it.
   *
   * @see java.util.regex.Matcher
   */
  public static Pattern forPath(String pathSpec) {
    return Pattern.compile(convertPathSpecToRegex(pathSpec));
  }

  private static String convertPathSpecToRegex(String pathSpec) {
    return PATHARG_MATCHER.matcher(pathSpec).replaceAll("(?<${name}>[^/]+)");
  }
}
