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
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.reporting.IcannReportingModule.ReportType.ACTIVITY;
import static google.registry.reporting.IcannReportingModule.ReportType.TRANSACTIONS;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.GcsTestingUtils.writeGcsFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.common.base.Optional;
import google.registry.gcs.GcsUtils;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.FakeSleeper;
import google.registry.util.Retrier;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link google.registry.reporting.IcannReportingUploadAction} */
@RunWith(JUnit4.class)
public class IcannReportingUploadActionTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  private static final byte[] FAKE_PAYLOAD = "test,csv\n13,37".getBytes(UTF_8);
  private final IcannHttpReporter mockReporter = mock(IcannHttpReporter.class);
  private final FakeResponse response = new FakeResponse();
  private final GcsService gcsService = GcsServiceFactory.createGcsService();
  private final GcsFilename reportFile =
      new GcsFilename("basin/icann/monthly/2017-05", "test-transactions-201705.csv");

  private IcannReportingUploadAction createAction() {
    IcannReportingUploadAction action = new IcannReportingUploadAction();
    action.icannReporter = mockReporter;
    action.gcsUtils = new GcsUtils(gcsService, 1024);
    action.retrier = new Retrier(new FakeSleeper(new FakeClock()), 3);
    action.yearMonth = "2017-05";
    action.reportType = TRANSACTIONS;
    action.subdir = Optional.absent();
    action.tld = "test";
    action.icannReportingBucket = "basin";
    action.response = response;
    return action;
  }

  @Before
  public void before() throws Exception {
    createTld("test");
    writeGcsFile(gcsService, reportFile, FAKE_PAYLOAD);
  }

  @Test
  public void testSuccess() throws Exception {
    IcannReportingUploadAction action = createAction();
    action.run();
    verify(mockReporter).send(FAKE_PAYLOAD, "test", "2017-05", TRANSACTIONS);
    verifyNoMoreInteractions(mockReporter);
    assertThat(((FakeResponse) action.response).getPayload())
        .isEqualTo("OK, sending: test,csv\n13,37");
  }

  @Test
  public void testSuccess_WithRetry() throws Exception {
    IcannReportingUploadAction action = createAction();
    doThrow(new IOException("Expected exception."))
        .doNothing()
        .when(mockReporter)
        .send(FAKE_PAYLOAD, "test", "2017-05", TRANSACTIONS);
    action.run();
    verify(mockReporter, times(2)).send(FAKE_PAYLOAD, "test", "2017-05", TRANSACTIONS);
    verifyNoMoreInteractions(mockReporter);
    assertThat(((FakeResponse) action.response).getPayload())
        .isEqualTo("OK, sending: test,csv\n13,37");
  }

  @Test
  public void testFail_NonexisistentTld() throws Exception {
    IcannReportingUploadAction action = createAction();
    action.tld = "invalidTld";
    try {
      action.run();
      assertWithMessage("Expected IllegalArgumentException to be thrown").fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("TLD invalidTld does not exist");
    }
  }

  @Test
  public void testFail_InvalidYearMonth() throws Exception {
    IcannReportingUploadAction action = createAction();
    action.yearMonth = "2017-3";
    try {
      action.run();
      assertWithMessage("Expected IllegalStateException to be thrown").fail();
    } catch (IllegalStateException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("yearMonth must be in YYYY-MM format, got 2017-3 instead.");
    }
  }

  @Test
  public void testFail_InvalidSubdir() throws Exception {
    IcannReportingUploadAction action = createAction();
    action.subdir = Optional.of("/subdir/with/slash");
    try {
      action.run();
      assertWithMessage("Expected IllegalStateException to be thrown").fail();
    } catch (IllegalStateException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(
              "subdir must not start or end with a \"/\", got /subdir/with/slash instead.");
    }
  }

  @Test
  public void testFail_FileNotFound() throws Exception {
    IcannReportingUploadAction action = createAction();
    action.yearMonth = "1234-56";
    try {
      action.run();
      assertWithMessage("Expected IllegalStateException to be thrown").fail();
    } catch (IllegalStateException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(
              "ICANN report object test-transactions-123456.csv "
                  + "in bucket basin/icann/monthly/1234-56 not found");
    }
  }

  @Test
  public void testSuccess_CreateFilename() throws Exception{
    assertThat(IcannReportingUploadAction.createFilename("test", "2017-05", ACTIVITY))
        .isEqualTo("test-activity-201705.csv");
    assertThat(IcannReportingUploadAction.createFilename("foo", "1234-56", TRANSACTIONS))
        .isEqualTo("foo-transactions-123456.csv");
  }

  @Test
  public void testSuccess_CreateBucketname() throws Exception{
    assertThat(
        IcannReportingUploadAction
            .createReportingBucketName("gs://my-reporting", Optional.<String>absent(), "2017-05"))
        .isEqualTo("gs://my-reporting/icann/monthly/2017-05");
    assertThat(
        IcannReportingUploadAction
            .createReportingBucketName("gs://my-reporting", Optional.of("manual"), "2017-05"))
        .isEqualTo("gs://my-reporting/manual");
  }
}
