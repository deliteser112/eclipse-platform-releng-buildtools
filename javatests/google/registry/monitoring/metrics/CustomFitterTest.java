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

package google.registry.monitoring.metrics;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CustomFitter}. */
@RunWith(JUnit4.class)
public class CustomFitterTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testCreateCustomFitter_emptyBounds_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("boundaries must not be empty");

    CustomFitter.create(ImmutableSet.<Double>of());
  }

  @Test
  public void testCreateCustomFitter_outOfOrderBounds_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("boundaries must be sorted");

    CustomFitter.create(ImmutableSet.of(2.0, 0.0));
  }

  @Test
  public void testCreateCustomFitter_hasGivenBounds() {
    CustomFitter fitter = CustomFitter.create(ImmutableSortedSet.of(1.0, 2.0));

    assertThat(fitter.boundaries()).containsExactly(1.0, 2.0).inOrder();
  }
}
