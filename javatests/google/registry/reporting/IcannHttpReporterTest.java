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

import static com.google.common.net.MediaType.CSV_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.Base64;
import com.google.api.client.util.StringUtils;
import com.google.common.io.ByteSource;
import google.registry.reporting.IcannReportingModule.ReportType;
import google.registry.request.HttpException.InternalServerErrorException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link google.registry.reporting.IcannHttpReporter}.
 */
@RunWith(JUnit4.class)
public class IcannHttpReporterTest {

  private static final ByteSource IIRDEA_GOOD_XML = ReportingTestData.get("iirdea_good.xml");
  private static final ByteSource IIRDEA_BAD_XML = ReportingTestData.get("iirdea_bad.xml");

  private MockLowLevelHttpRequest mockRequest;

  private MockHttpTransport createMockTransport (final ByteSource iirdeaResponse) {
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        mockRequest = new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setStatusCode(200);
            response.setContentType(PLAIN_TEXT_UTF_8.toString());
            response.setContent(iirdeaResponse.read());
            return response;
          }
        };
        mockRequest.setUrl(url);
        return mockRequest;
      }
    };
  }

  private static final byte[] FAKE_PAYLOAD = "test,csv\n1,2".getBytes(UTF_8);

  private IcannHttpReporter createReporter() {
    IcannHttpReporter reporter = new IcannHttpReporter();
    reporter.httpTransport = createMockTransport(IIRDEA_GOOD_XML);
    reporter.password = "fakePass";
    reporter.icannTransactionsUrl = "https://fake-transactions.url";
    reporter.icannActivityUrl = "https://fake-activity.url";
    return reporter;
  }

  @Test
  public void testSuccess() throws Exception {
    IcannHttpReporter reporter = createReporter();
    reporter.send(FAKE_PAYLOAD, "test", "2017-06", ReportType.TRANSACTIONS);

    assertThat(mockRequest.getUrl()).isEqualTo("https://fake-transactions.url/test/2017-06");
    Map<String, List<String>> headers = mockRequest.getHeaders();
    String userPass = "test_ry:fakePass";
    String expectedAuth =
        String.format("Basic %s", Base64.encodeBase64String(StringUtils.getBytesUtf8(userPass)));
    assertThat(headers.get("authorization")).containsExactly(expectedAuth);
    assertThat(headers.get("content-type")).containsExactly(CSV_UTF_8.toString());
  }

  @Test
  public void testFail_BadIirdeaResponse() throws Exception {
    IcannHttpReporter reporter = createReporter();
    reporter.httpTransport = createMockTransport(IIRDEA_BAD_XML);
    try {
      reporter.send(FAKE_PAYLOAD, "test", "2017-06", ReportType.TRANSACTIONS);
      assertWithMessage("Expected InternalServerErrorException to be thrown").fail();
    } catch (InternalServerErrorException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("The structure of the report is invalid.");
    }
  }
}
