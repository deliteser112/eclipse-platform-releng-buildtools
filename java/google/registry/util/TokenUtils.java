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

package google.registry.util;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * A utility class for generating various auth tokens using a common prefixed-based format.
 * These tokens are generally of the form [TYPE]_[randomstring].
 */
public final class TokenUtils {

  /** An enum containing definitions (prefix and length) for token types. */
  public enum TokenType {
    ANCHOR_TENANT("ANCHOR", 16),
    LRP("LRP", 16);

    private final String prefix;
    private final int length;

    TokenType(String prefix, int length) {
      this.prefix = prefix;
      this.length = length;
    }

    /** Returns the prefix for a given type. */
    public String getPrefix() {
      return prefix;
    }

    /** Returns the set token length for a given type (not including the prefix). */
    public int getLength() {
      return length;
    }
  }

  /** Generates a single token of a given {@link TokenType}. */
  public static String createToken(TokenType type, StringGenerator generator) {
    return Iterables.getOnlyElement(createTokens(type, generator, 1));
  }

  /** Generates an {@link ImmutableSet} of tokens of a given {@link TokenType}. */
  public static ImmutableSet<String> createTokens(
      final TokenType type,
      StringGenerator generator,
      int count) {
    return generator
        .createStrings(type.getLength(), count)
        .stream()
        .map(token -> String.format("%s_%s", type.getPrefix(), token))
        .collect(toImmutableSet());
  }

  private TokenUtils() {}
}

