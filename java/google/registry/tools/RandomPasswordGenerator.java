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

import java.util.Random;
import javax.inject.Inject;

/** Password generator. */
class RandomPasswordGenerator implements PasswordGenerator {

  private static final String SYMBOLS =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-=";

  private final Random random;

  @Inject
  RandomPasswordGenerator(Random random) {
    this.random = random;
  }

  /** Generates a password of a specified length. */
  @Override
  public String createPassword(int length) {
    checkArgument(length > 0);
    char[] password = new char[length];
    for (int i = 0; i < length; ++i) {
      password[i] = SYMBOLS.charAt(random.nextInt(SYMBOLS.length()));
    }
    return new String(password);
  }
}
