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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LinearFitter}. */
@RunWith(JUnit4.class)
public class LinearFitterTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testCreateLinearFitter_zeroNumIntervals_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("numFiniteIntervals must be greater than 0");

    LinearFitter.create(0, 3.0, 0.0);
  }

  @Test
  public void testCreateLinearFitter_negativeNumIntervals_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("numFiniteIntervals must be greater than 0");

    LinearFitter.create(0, 3.0, 0.0);
  }

  @Test
  public void testCreateLinearFitter_zeroWidth_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("width must be greater than 0");

    LinearFitter.create(3, 0.0, 0.0);
  }

  @Test
  public void testCreateLinearFitter_negativeWidth_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("width must be greater than 0");

    LinearFitter.create(3, 0.0, 0.0);
  }

  @Test
  public void testCreateLinearFitter_NaNWidth_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("width must be greater than 0");

    LinearFitter.create(3, Double.NaN, 0.0);
  }

  @Test
  public void testCreateLinearFitter_NaNOffset_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value must be finite, not NaN, and not -0.0");

    LinearFitter.create(3, 1.0, Double.NaN);
  }

  @Test
  public void testCreateLinearFitter_hasCorrectBounds() {
    LinearFitter fitter = LinearFitter.create(1, 10, 0);

    assertThat(fitter.boundaries()).containsExactly(0.0, 10.0).inOrder();
  }

  @Test
  public void testCreateLinearFitter_withOffset_hasCorrectBounds() {
    LinearFitter fitter = LinearFitter.create(1, 10, 5);

    assertThat(fitter.boundaries()).containsExactly(5.0, 15.0).inOrder();
  }

  @Test
  public void testCreateLinearFitter_withOffsetAndMultipleIntervals_hasCorrectBounds() {
    LinearFitter fitter = LinearFitter.create(3, 10, 5);

    assertThat(fitter.boundaries()).containsExactly(5.0, 15.0, 25.0, 35.0).inOrder();
  }
}
