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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import google.registry.reporting.IcannReportingModule.ReportType;
import google.registry.request.HttpException.BadRequestException;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.util.Clock;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;
import org.joda.time.YearMonth;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link google.registry.reporting.IcannReportingModule}. */
@RunWith(JUnit4.class)
public class IcannReportingModuleTest {

  HttpServletRequest req = mock(HttpServletRequest.class);
  Clock clock;

  @Rule public final ExceptionRule thrown = new ExceptionRule();

  @Before
  public void setUp() {
    clock = new FakeClock(DateTime.parse("2017-07-01TZ"));
  }

  @Test
  public void testEmptyYearMonthParameter_returnsEmptyYearMonthOptional() {
    when(req.getParameter("yearMonth")).thenReturn("");
    assertThat(IcannReportingModule.provideYearMonthOptional(req)).isEqualTo(Optional.empty());
  }

  @Test
  public void testInvalidYearMonthParameter_throwsException() {
    when(req.getParameter("yearMonth")).thenReturn("201705");
    thrown.expect(
        BadRequestException.class, "yearMonth must be in yyyy-MM format, got 201705 instead");
    IcannReportingModule.provideYearMonthOptional(req);
  }

  @Test
  public void testEmptyYearMonth_returnsLastMonth() {
    assertThat(IcannReportingModule.provideYearMonth(Optional.empty(), clock))
        .isEqualTo(new YearMonth(2017, 6));
  }

  @Test
  public void testGivenYearMonth_returnsThatMonth() {
    assertThat(IcannReportingModule.provideYearMonth(Optional.of(new YearMonth(2017, 5)), clock))
        .isEqualTo(new YearMonth(2017, 5));
  }

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
    thrown.expect(
        BadRequestException.class,
        "subdir must not start or end with a \"/\", got /whoops instead.");
    IcannReportingModule.provideSubdir(Optional.of("/whoops"), new YearMonth(2017, 6));
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
