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

package google.registry.reporting.icann;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.JUnitBackports.expectThrows;

import google.registry.reporting.icann.IcannReportingModule.ReportType;
import google.registry.request.HttpException.BadRequestException;
import java.util.Optional;
import org.joda.time.YearMonth;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link google.registry.reporting.icann.IcannReportingModule}. */
@RunWith(JUnit4.class)
public class IcannReportingModuleTest {

  @Test
  public void testEmptySubDir_returnsDefaultSubdir() {
    assertThat(IcannReportingModule.provideSubdir(Optional.empty(), new YearMonth(2017, 6)))
        .isEqualTo("icann/monthly/2017-06");
  }

  @Test
  public void testGivenSubdir_returnsManualSubdir() {
    assertThat(
            IcannReportingModule.provideSubdir(Optional.of("manual/dir"), new YearMonth(2017, 6)))
        .isEqualTo("manual/dir");
  }

  @Test
  public void testInvalidSubdir_throwsException() {
    BadRequestException thrown =
        expectThrows(
            BadRequestException.class,
            () ->
                IcannReportingModule.provideSubdir(Optional.of("/whoops"), new YearMonth(2017, 6)));
    assertThat(thrown)
        .hasMessageThat()
        .contains("subdir must not start or end with a \"/\", got /whoops instead.");
  }

  @Test
  public void testGivenReportType_returnsReportType() {
    assertThat(IcannReportingModule.provideReportTypes(Optional.of(ReportType.ACTIVITY)))
        .containsExactly(ReportType.ACTIVITY);
  }

  @Test
  public void testNoReportType_returnsBothReportTypes() {
    assertThat(IcannReportingModule.provideReportTypes(Optional.empty()))
        .containsExactly(ReportType.ACTIVITY, ReportType.TRANSACTIONS);
  }
}
