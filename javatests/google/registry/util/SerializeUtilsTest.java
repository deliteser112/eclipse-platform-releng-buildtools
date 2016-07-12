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

package google.registry.util;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.util.SerializeUtils.deserialize;
import static google.registry.util.SerializeUtils.serialize;

import google.registry.testing.ExceptionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SerializeUtils}. */
@RunWith(JUnit4.class)
public class SerializeUtilsTest {

  class Lol {
    @Override
    public String toString() {
      return "LOL_VALUE";
    }
  }

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testSerialize_nullValue_returnsNull() throws Exception {
    assertThat(serialize(null)).isNull();
  }

  @Test
  public void testDeserialize_nullValue_returnsNull() throws Exception {
    assertThat(deserialize(Object.class, null)).isNull();
  }

  @Test
  public void testSerializeDeserialize_stringValue_maintainsValue() throws Exception {
    assertThat(deserialize(String.class, serialize("hello"))).isEqualTo("hello");
  }

  @Test
  public void testSerialize_objectDoesntImplementSerialize_hasInformativeError() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Unable to serialize: LOL_VALUE");
    serialize(new Lol());
  }

  @Test
  public void testDeserialize_badValue_hasInformativeError() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Unable to deserialize: objectBytes=FF");
    deserialize(String.class, new byte[] { (byte) 0xff });
  }
}
