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

import com.beust.jcommander.ParameterException;
import google.registry.testing.ExceptionRule;
import org.joda.time.YearMonth;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link YearMonthParameter}. */
@RunWith(JUnit4.class)
public class YearMonthParameterTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final YearMonthParameter instance = new YearMonthParameter();

  @Test
  public void testConvert_awfulMonth() throws Exception {
    assertThat(instance.convert("1984-12")).isEqualTo(new YearMonth(1984, 12));
  }

  @Test
  public void testConvert_null_throwsException() throws Exception {
    thrown.expect(NullPointerException.class);
    instance.convert(null);
  }

  @Test
  public void testConvert_empty_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("");
  }

  @Test
  public void testConvert_sillyString_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("foo");
  }

  @Test
  public void testConvert_wrongOrder() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("12-1984");
  }

  @Test
  public void testConvert_noHyphen() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("198412");
  }

  @Test
  public void testValidate_sillyString_throwsParameterException() throws Exception {
    thrown.expect(ParameterException.class, "--time=foo not a valid");
    instance.validate("--time", "foo");
  }

  @Test
  public void testValidate_correctInput_doesntThrow() throws Exception {
    instance.validate("--time", "1984-12");
  }
}
