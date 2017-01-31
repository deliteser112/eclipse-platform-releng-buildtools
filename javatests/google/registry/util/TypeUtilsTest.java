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

import static com.google.common.truth.Truth.assertThat;

import google.registry.testing.ExceptionRule;
import java.io.Serializable;
import java.util.ArrayList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TypeUtils}. */
@RunWith(JUnit4.class)
public class TypeUtilsTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void test_getClassFromString_validClass() {
    Class<? extends Serializable> clazz =
        TypeUtils.<Serializable>getClassFromString("java.util.ArrayList", Serializable.class);
    assertThat(clazz).isEqualTo(ArrayList.class);
  }

  @Test
  public void test_getClassFromString_notAssignableFrom() {
    thrown.expect(IllegalArgumentException.class, "ArrayList does not implement/extend Integer");
    TypeUtils.<Integer>getClassFromString("java.util.ArrayList", Integer.class);
  }

  @Test
  public void test_getClassFromString_unknownClass() {
    thrown.expect(
        IllegalArgumentException.class, "Failed to load class com.fake.company.nonexistent.Class");
    TypeUtils.<Object>getClassFromString("com.fake.company.nonexistent.Class", Object.class);
  }
}
