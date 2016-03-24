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

package com.google.domain.registry.model.common;

import static com.google.common.truth.Truth.assertThat;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TimeOfYear}. */
@RunWith(JUnit4.class)
public class TimeOfYearTest {

  private static final DateTime february28 = DateTime.parse("2012-02-28T01:02:03.0Z");
  private static final DateTime february29 = DateTime.parse("2012-02-29T01:02:03.0Z");
  private static final DateTime march1 = DateTime.parse("2012-03-01T01:02:03.0Z");

  @Test
  public void testFromDateTime() throws Exception {
    // We intentionally don't allow leap years in TimeOfYear, so February 29 should be February 28.
    assertThat(TimeOfYear.fromDateTime(february28)).isEqualTo(TimeOfYear.fromDateTime(february29));
    assertThat(TimeOfYear.fromDateTime(february29)).isNotEqualTo(TimeOfYear.fromDateTime(march1));
  }

  @Test
  public void testNextAfter() throws Exception {
    // This should be lossless because atOrAfter includes an exact match.
    assertThat(TimeOfYear.fromDateTime(march1).atOrAfter(march1)).isEqualTo(march1);
    // This should be a year later because we stepped forward a millisecond
    assertThat(TimeOfYear.fromDateTime(march1).atOrAfter(march1.plusMillis(1)))
        .isEqualTo(march1.plusYears(1));
  }

  @Test
  public void testNextBefore() throws Exception {
    // This should be lossless because beforeOrAt includes an exact match.
    assertThat(TimeOfYear.fromDateTime(march1).beforeOrAt(march1)).isEqualTo(march1);
    // This should be a year earlier because we stepped backward a millisecond
    assertThat(TimeOfYear.fromDateTime(march1).beforeOrAt(march1.minusMillis(1)))
        .isEqualTo(march1.minusYears(1));
  }
}
