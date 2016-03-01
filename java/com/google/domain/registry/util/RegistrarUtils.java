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

import static com.google.common.base.CharMatcher.javaLetterOrDigit;

/** Utilities for working with {@code Registrar} objects. */
public class RegistrarUtils {
  /** Strip out anything that isn't a letter or digit, and lowercase. */
  public static String normalizeRegistrarName(String name) {
    return javaLetterOrDigit().retainFrom(name).toLowerCase();
  }

  /**
   * Returns a normalized registrar clientId by taking the input and making it lowercase and
   * removing all characters that aren't alphanumeric or hyphens. The normalized id should be unique
   * in Datastore, and is suitable for use in email addresses.
   */
  public static String normalizeClientId(String clientId) {
    return clientId.toLowerCase().replaceAll("[^a-z0-9\\-]", "");
  }
}
