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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.charactersOf;

import com.google.common.collect.Iterators;
import java.util.Iterator;

/** A password generator that produces a password from a predefined string. */
class FakePasswordGenerator implements PasswordGenerator {

  private Iterator<Character> iterator;

  /** Produces a password from the password source string. */
  @Override
  public String createPassword(int length) {
    checkArgument(length > 0, "Password length must be positive.");
    String password = "";
    for (int i = 0; i < length; i++) {
      password += iterator.next();
    }
    return password;
  }

  public FakePasswordGenerator(String passwordSource) {
    checkArgument(!isNullOrEmpty(passwordSource), "Password source cannot be null or empty.");
    iterator = Iterators.cycle(charactersOf(passwordSource));
  }
}
