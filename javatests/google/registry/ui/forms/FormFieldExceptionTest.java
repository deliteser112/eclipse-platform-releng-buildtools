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

package google.registry.ui.forms;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.testing.NullPointerTester;
import google.registry.testing.ExceptionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FormFieldException}. */
@RunWith(JUnit4.class)
public class FormFieldExceptionTest {

  @Rule
  public ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testGetFieldName_multiplePropagations_joinsUsingJsonNotation() throws Exception {
    assertThat(
        new FormFieldException("This field is required.")
            .propagate("attack")
            .propagate("cat")
            .propagate(0)
            .propagate("lol")
            .getFieldName())
                .isEqualTo("lol[0].cat.attack");
  }

  @Test
  public void testGetFieldName_singlePropagations_noFancyJoining() throws Exception {
    assertThat(
        new FormFieldException("This field is required.")
            .propagate("cat")
            .getFieldName())
                .isEqualTo("cat");
  }

  @Test
  public void testGetFieldName_noPropagations_throwsIse() throws Exception {
    thrown.expect(IllegalStateException.class);
    new FormFieldException("This field is required.").getFieldName();
  }

  @Test
  public void testNullness() {
    NullPointerTester tester = new NullPointerTester()
        .setDefault(FormField.class, FormField.named("love").build());
    tester.testAllPublicConstructors(FormFieldException.class);
    tester.testAllPublicStaticMethods(FormFieldException.class);
    tester.testAllPublicInstanceMethods(new FormFieldException("lol"));
  }
}
