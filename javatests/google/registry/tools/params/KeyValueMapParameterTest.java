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

package google.registry.tools.params;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import google.registry.testing.ExceptionRule;
import google.registry.tools.params.KeyValueMapParameter.StringToIntegerMap;
import google.registry.tools.params.KeyValueMapParameter.StringToStringMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link KeyValueMapParameter}. */
@RunWith(JUnit4.class)
public class KeyValueMapParameterTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final StringToStringMap stringToStringInstance = new StringToStringMap();
  private final StringToIntegerMap stringToIntegerInstance = new StringToIntegerMap();

  @Test
  public void testSuccess_convertStringToString_singleEntry() throws Exception {
    assertThat(stringToStringInstance.convert("key=foo"))
        .isEqualTo(ImmutableMap.of("key", "foo"));
  }

  @Test
  public void testSuccess_convertStringToInteger_singleEntry() throws Exception {
    assertThat(stringToIntegerInstance.convert("key=1"))
        .isEqualTo(ImmutableMap.of("key", 1));
  }

  @Test
  public void testSuccess_convertStringToString() throws Exception {
    assertThat(stringToStringInstance.convert("key=foo,key2=bar"))
        .isEqualTo(ImmutableMap.of("key", "foo", "key2", "bar"));
  }

  @Test
  public void testSuccess_convertStringToInteger() throws Exception {
    assertThat(stringToIntegerInstance.convert("key=1,key2=2"))
        .isEqualTo(ImmutableMap.of("key", 1, "key2", 2));
  }

  @Test
  public void testSuccess_convertStringToString_empty() throws Exception {
    assertThat(stringToStringInstance.convert("")).isEmpty();
  }

  @Test
  public void testSuccess_convertStringToInteger_empty() throws Exception {
    assertThat(stringToIntegerInstance.convert("")).isEmpty();
  }

  @Test
  public void testFailure_convertStringToInteger_badType() throws Exception {
    thrown.expect(NumberFormatException.class);
    stringToIntegerInstance.convert("key=1,key2=foo");
  }

  @Test
  public void testFailure_convertStringToString_badSeparator() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    stringToStringInstance.convert("key=foo&key2=bar");
  }

  @Test
  public void testFailure_convertStringToInteger_badSeparator() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    stringToIntegerInstance.convert("key=1&key2=2");
  }

  @Test
  public void testFailure_convertStringToString_badFormat() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    stringToStringInstance.convert("foo");
  }

  @Test
  public void testFailure_convertStringToInteger_badFormat() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    stringToIntegerInstance.convert("foo");
  }
}
