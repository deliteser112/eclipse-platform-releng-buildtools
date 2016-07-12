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

import google.registry.testing.ExceptionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SqlTemplate}. */
@RunWith(JUnit4.class)
public class SqlTemplateTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testFillSqlTemplate() throws Exception {
    assertThat(
        SqlTemplate.create("%TEST%")
            .put("TEST", "hello world")
            .build())
                .isEqualTo("hello world");
    assertThat(
        SqlTemplate.create("one %TWO% three")
            .put("TWO", "2")
            .build())
                .isEqualTo("one 2 three");
    assertThat(
        SqlTemplate.create("%ONE% %TWO% %THREE%")
            .put("ONE", "1")
            .put("TWO", "2")
            .put("THREE", "3")
            .build())
                .isEqualTo("1 2 3");
  }

  @Test
  public void testFillSqlTemplate_substitutionButNoVariables() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Not found in template: ONE");
    SqlTemplate.create("")
        .put("ONE", "1")
        .build();
  }

  @Test
  public void testFillSqlTemplate_substitutionButMissingVariables() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Not found in template: TWO");
    SqlTemplate.create("%ONE%")
        .put("ONE", "1")
        .put("TWO", "2")
        .build();
  }

  @Test
  public void testFillSqlTemplate_sameKeyTwice_failsEarly() throws Exception {
    thrown.expect(IllegalArgumentException.class, "");
    SqlTemplate.create("%ONE%")
        .put("ONE", "1")
        .put("ONE", "2");
  }

  @Test
  public void testFillSqlTemplate_variablesButNotEnoughSubstitutions() throws Exception {
    thrown.expect(IllegalArgumentException.class, "%TWO% found in template but no substitution");
    SqlTemplate.create("%ONE% %TWO%")
        .put("ONE", "1")
        .build();
  }

  @Test
  public void testFillSqlTemplate_mismatchedVariableAndSubstitution() throws Exception {
    thrown.expect(IllegalArgumentException.class, "%TWO% found in template but no substitution");
    SqlTemplate.create("%TWO%")
        .put("TOO", "2")
        .build();
  }

  @Test
  public void testFillSqlTemplate_missingKeyVals_whatsThePoint() throws Exception {
    thrown.expect(IllegalArgumentException.class, "%TWO% found in template but no substitution");
    SqlTemplate.create("%TWO%")
        .build();
  }

  @Test
  public void testFillSqlTemplate_lowercaseKey_notAllowed() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Bad substitution key: test");
    SqlTemplate.create("%test%")
        .put("test", "hello world")
        .build();
  }

  @Test
  public void testFillSqlTemplate_substitution_disallowsSingleQuotes() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Illegal characters in foo'bar");
    SqlTemplate.create("The words are '%LOS%' and baz")
        .put("LOS", "foo'bar");
  }

  @Test
  public void testFillSqlTemplate_substitution_disallowsDoubleQuotes() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Illegal characters in foo\"bar");
    SqlTemplate.create("The words are '%LOS%' and baz")
        .put("LOS", "foo\"bar");
  }

  @Test
  public void testFillSqlTemplate_quoteMismatch_throwsError() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Quote mismatch: \"%LOS%'");
    SqlTemplate.create("The words are \"%LOS%' and baz")
        .put("LOS", "foobar")
        .build();
  }

  @Test
  public void testFillSqlTemplate_extendedQuote_throwsError() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Quote mismatch: '%LOS%");
    SqlTemplate.create("The words are '%LOS%-lol' and baz")
        .put("LOS", "roid")
        .build();
  }
}
