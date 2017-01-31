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

package google.registry.model.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Range;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PersistedRangeLong}. */
@RunWith(JUnit4.class)
public class PersistedRangeLongTest {

  private void doConversionTest(Range<Long> range) {
    assertThat(PersistedRangeLong.create(range).asRange()).isEqualTo(range);
  }

  @Test
  public void all() {
    doConversionTest(Range.<Long>all());
  }

  @Test
  public void lessThan() {
    doConversionTest(Range.<Long>lessThan(10L));
  }

  @Test
  public void atMost() {
    doConversionTest(Range.<Long>atMost(10L));
  }

  @Test
  public void greaterThan() {
    doConversionTest(Range.<Long>greaterThan(10L));
  }

  @Test
  public void atLeast() {
    doConversionTest(Range.<Long>atLeast(10L));
  }

  @Test
  public void open() {
    doConversionTest(Range.<Long>open(5L, 10L));
  }

  @Test
  public void closed() {
    doConversionTest(Range.<Long>closed(5L, 10L));
  }

  @Test
  public void openClosed() {
    doConversionTest(Range.<Long>openClosed(5L, 10L));
  }

  @Test
  public void closedOpen() {
    doConversionTest(Range.<Long>closedOpen(5L, 10L));
  }

  @Test
  public void singleton() {
    doConversionTest(Range.<Long>singleton(5L));
  }

  @Test
  public void openClosedEmpty() {
    doConversionTest(Range.<Long>openClosed(5L, 5L));
  }

  @Test
  public void closedOpenEmpty() {
    doConversionTest(Range.<Long>closedOpen(5L, 5L));
  }
}
