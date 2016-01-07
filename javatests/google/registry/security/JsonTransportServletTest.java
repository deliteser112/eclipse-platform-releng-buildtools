// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.security;

import static com.google.common.net.HttpHeaders.CONTENT_DISPOSITION;
import static com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.truth.Truth.assertThat;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import google.registry.request.HttpException;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link JsonTransportServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class JsonTransportServletTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private StringWriter writer = new StringWriter();

  @Mock
  HttpServletRequest req;

  @Mock
  HttpServletResponse rsp;

  static class TestServlet extends JsonTransportServlet {
    private Map<String, Object> responseMap;

    TestServlet(Map<String, Object> responseMap) {
      super("foo", false);
      this.responseMap = responseMap;
    }

    @Override
    public Map<String, Object> doJsonPost(HttpServletRequest req, Map<String, ?> input) {
      return responseMap;
    }
  }

  private void verifySuccess(String json) {
    verify(rsp).setStatus(SC_OK);
    verify(rsp).setHeader(CONTENT_DISPOSITION, "attachment");
    verify(rsp).setHeader(X_CONTENT_TYPE_OPTIONS, "nosniff");
    verify(rsp).setContentType(JSON_UTF_8.toString());
    assertThat(writer.toString()).isEqualTo(")]}'\n" + json);
  }

  private void doSuccessfulTest(
      String requestJson, Map<String, Object> responseMap) throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getContentType()).thenReturn(JSON_UTF_8.toString());
    when(req.getReader()).thenReturn(new BufferedReader(new StringReader(requestJson)));
    when(rsp.getWriter()).thenReturn(new PrintWriter(writer));
    new TestServlet(responseMap).doPost(req, rsp);
    verifySuccess("{\"a\":1}");
  }

  private void verifyFailure(int error) throws Exception {
    verify(rsp).sendError(eq(error), anyString());
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest("{\"key\":\"value\"}", ImmutableMap.<String, Object>of("a", 1));
  }

  @Test
  public void testDoJsonPost_returnsNull_notAllowed() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getContentType()).thenReturn(JSON_UTF_8.toString());
    when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{\"key\":\"value\"}")));
    when(rsp.getWriter()).thenReturn(new PrintWriter(writer));
    thrown.expect(NullPointerException.class);
    new TestServlet(null).doPost(req, rsp);
  }

  private void doInvalidRequestTest(String requestJson) throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getContentType()).thenReturn(JSON_UTF_8.toString());
    when(req.getReader()).thenReturn(new BufferedReader(new StringReader(requestJson)));
    new TestServlet(null).doPost(req, rsp);
    verifyFailure(SC_BAD_REQUEST);
  }

  @Test
  public void testSuccess_emptyJsonNotAllowed() throws Exception {
    doInvalidRequestTest("");
  }

  @Test
  public void testFailure_badJson() throws Exception {
    doInvalidRequestTest("{}{}");
  }

  @Test
  public void testFailure_nonObjectJson_null() throws Exception {
    doInvalidRequestTest("null");
  }

  @Test
  public void testFailure_nonObjectJson_array() throws Exception {
    doInvalidRequestTest("[]");
  }

  @Test
  public void testErrorMessagesAreEscaped() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));
    when(req.getContentType()).thenReturn(JSON_UTF_8.toString());
    new TestServlet(null) {
      @Override
      public Map<String, Object> doJsonPost(HttpServletRequest req, Map<String, ?> input) {
        throw new HttpException(123, "<script>", null){};
      }}.doPost(req, rsp);
    verify(rsp).sendError(123, "&lt;script&gt;");
  }
}
