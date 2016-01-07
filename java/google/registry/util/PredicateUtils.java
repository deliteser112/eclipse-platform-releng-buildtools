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

package google.registry.util;

import com.google.common.base.Predicate;

/** Utility class containing {@link Predicate} methods. */
public class PredicateUtils {

  /**
   * A predicate for a given class X that checks if tested classes are supertypes of X.
   *
   * <p>We need our own predicate because Guava's class predicates are backwards.
   * @see "https://github.com/google/guava/issues/1444"
   */
  private static class SupertypeOfPredicate implements Predicate<Class<?>> {

    private final Class<?> subClass;

    SupertypeOfPredicate(Class<?> subClass) {
      this.subClass = subClass;
    }

    @Override
    public boolean apply(Class<?> superClass) {
      return superClass.isAssignableFrom(subClass);
    }
  }

  public static Predicate<Class<?>> supertypeOf(Class<?> subClass) {
    return new SupertypeOfPredicate(subClass);
  }
}
