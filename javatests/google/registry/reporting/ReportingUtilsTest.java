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

package google.registry.reporting;

import static com.google.common.truth.Truth.assertThat;

import google.registry.reporting.IcannReportingModule.ReportType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link google.registry.reporting.ReportingUtils}. */
@RunWith(JUnit4.class)
public class ReportingUtilsTest {
  @Test
  public void testCreateFilename_success() {
    assertThat(ReportingUtils.createFilename("test", "2017-06", ReportType.ACTIVITY))
        .isEqualTo("test-activity-201706.csv");
    assertThat(ReportingUtils.createFilename("foo", "2017-06", ReportType.TRANSACTIONS))
        .isEqualTo("foo-transactions-201706.csv");
  }

  @Test
  public void testCreateBucketName_success() {
    assertThat(ReportingUtils.createReportingBucketName("gs://domain-registry-basin", "my/subdir"))
        .isEqualTo("gs://domain-registry-basin/my/subdir");
  }
}
