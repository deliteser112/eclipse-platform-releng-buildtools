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

package google.registry.tools.params;

import static com.google.common.truth.Truth.assertThat;

import google.registry.testing.ExceptionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PhoneNumberParameter}. */
@RunWith(JUnit4.class)
public class PhoneNumberParameterTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final OptionalPhoneNumberParameter instance = new OptionalPhoneNumberParameter();

  @Test
  public void testConvert_e164() throws Exception {
    assertThat(instance.convert("+1.2125550777")).hasValue("+1.2125550777");
  }

  @Test
  public void testConvert_sillyString_throws() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("foo");
  }

  @Test
  public void testConvert_empty_returnsAbsent() throws Exception {
    assertThat(instance.convert("")).isAbsent();
  }

  @Test
  public void testConvert_nullString_returnsAbsent() throws Exception {
    assertThat(instance.convert("null")).isAbsent();
  }
}
