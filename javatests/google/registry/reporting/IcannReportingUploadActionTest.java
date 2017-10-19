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
  private static final byte[] MANIFEST_PAYLOAD = "test-transactions-201706.csv\n".getBytes(UTF_8);
  private final IcannHttpReporter mockReporter = mock(IcannHttpReporter.class);
  private final FakeResponse response = new FakeResponse();
  private final GcsService gcsService = GcsServiceFactory.createGcsService();
  private final GcsFilename reportFile =
      new GcsFilename("basin/icann/monthly/2017-06", "test-transactions-201706.csv");
  private final GcsFilename manifestFile =
      new GcsFilename("basin/icann/monthly/2017-06", "MANIFEST.txt");

  private IcannReportingUploadAction createAction() {
    IcannReportingUploadAction action = new IcannReportingUploadAction();
    action.icannReporter = mockReporter;
    action.gcsUtils = new GcsUtils(gcsService, 1024);
    action.retrier = new Retrier(new FakeSleeper(new FakeClock()), 3);
    action.subdir = "icann/monthly/2017-06";
    action.reportingBucket = "basin";
    action.response = response;
    return action;
  }

  @Before
  public void before() throws Exception {
    writeGcsFile(gcsService, reportFile, FAKE_PAYLOAD);
    writeGcsFile(gcsService, manifestFile, MANIFEST_PAYLOAD);
  }

  @Test
  public void testSuccess() throws Exception {
    IcannReportingUploadAction action = createAction();
    action.run();
    verify(mockReporter).send(FAKE_PAYLOAD, "test-transactions-201706.csv");
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
        .send(FAKE_PAYLOAD, "test-transactions-201706.csv");
    action.run();
    verify(mockReporter, times(2)).send(FAKE_PAYLOAD, "test-transactions-201706.csv");
    verifyNoMoreInteractions(mockReporter);
    assertThat(((FakeResponse) action.response).getPayload())
        .isEqualTo("OK, sending: test,csv\n13,37");
  }

  @Test
  public void testFail_FileNotFound() throws Exception {
    IcannReportingUploadAction action = createAction();
    action.subdir = "somewhere/else";
    try {
      action.run();
      assertWithMessage("Expected IllegalStateException to be thrown").fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Object MANIFEST.txt in bucket basin/somewhere/else not found");
    }
  }
}

