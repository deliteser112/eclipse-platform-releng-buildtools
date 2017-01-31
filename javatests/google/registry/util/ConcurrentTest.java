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
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.NullPointerTester;
import com.google.common.util.concurrent.UncheckedExecutionException;
import google.registry.testing.AppEngineRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Concurrent}. */
@RunWith(JUnit4.class)
public class ConcurrentTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Test
  public void testTransform_emptyList_returnsEmptyList() throws Exception {
    assertThat(Concurrent.transform(ImmutableList.of(), Functions.identity())).isEmpty();
  }

  @Test
  public void testTransform_addIntegers() throws Exception {
    assertThat(Concurrent.transform(ImmutableList.of(1, 2, 3), new Function<Integer, Integer>() {
      @Override
      public Integer apply(Integer input) {
        return input + 1;
      }})).containsExactly(2, 3, 4).inOrder();
  }

  @Test
  public void testTransform_throwsException_isSinglyWrappedByUee() throws Exception {
    try {
      Concurrent.transform(ImmutableList.of(1, 2, 3), new Function<Integer, Integer>() {
        @Override
        public Integer apply(Integer input) {
          throw new RuntimeException("hello");
        }});
      fail("Didn't throw!");
    } catch (UncheckedExecutionException e) {
      // We can't use ExpectedException because root cause must be one level of indirection away.
      assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
      assertThat(e.getCause()).hasMessage("hello");
    }
  }

  @Test
  public void testNullness() throws Exception {
    NullPointerTester tester = new NullPointerTester()
        .setDefault(Function.class, Functions.identity());
    tester.testAllPublicStaticMethods(Concurrent.class);
  }
}
