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

package google.registry.util;

import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.CSV_UTF_8;
import static google.registry.util.UrlFetchUtils.setPayloadMultipart;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import google.registry.testing.AppEngineRule;
import google.registry.testing.InjectRule;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/** Unit tests for {@link UrlFetchUtils}. */
@RunWith(JUnit4.class)
public class UrlFetchUtilsTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Before
  public void setupRandomZeroes() throws Exception {
    SecureRandom secureRandom = mock(SecureRandom.class);
    inject.setStaticField(UrlFetchUtils.class, "secureRandom", secureRandom);
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock info) throws Throwable {
        byte[] bytes = (byte[]) info.getArguments()[0];
        Arrays.fill(bytes, (byte) 0);
        return null;
      }}).when(secureRandom).nextBytes(any(byte[].class));
  }

  @Test
  public void testSetPayloadMultipart() throws Exception {
    String payload = "--------------------------------AAAAAAAA\r\n"
        + "Content-Disposition: form-data; name=\"lol\"; filename=\"cat\"\r\n"
        + "Content-Type: text/csv; charset=utf-8\r\n"
        + "\r\n"
        + "The nice people at the store say hello. ヘ(◕。◕ヘ)\r\n"
        + "--------------------------------AAAAAAAA--";
    HTTPRequest request = mock(HTTPRequest.class);
    setPayloadMultipart(
        request, "lol", "cat", CSV_UTF_8, "The nice people at the store say hello. ヘ(◕。◕ヘ)");
    verify(request).addHeader(argThat(new HTTPHeaderMatcher(
        CONTENT_TYPE, "multipart/form-data; boundary=------------------------------AAAAAAAA")));
    verify(request).addHeader(argThat(new HTTPHeaderMatcher(CONTENT_LENGTH, "244")));
    verify(request).setPayload(payload.getBytes(UTF_8));
    verifyNoMoreInteractions(request);
  }

  /** Mockito matcher for {@link HTTPHeader}. */
  public static class HTTPHeaderMatcher extends ArgumentMatcher<HTTPHeader> {
    private final String name;
    private final String value;

    public HTTPHeaderMatcher(String name, String value) {
      this.name = name;
      this.value = value;
    }

    @Override
    public boolean matches(Object arg) {
      HTTPHeader header = (HTTPHeader) arg;
      return name.equals(header.getName())
          && value.equals(header.getValue());
    }
  }
}
