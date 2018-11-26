// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools.server;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.io.BaseEncoding;
import com.google.common.net.MediaType;
import google.registry.testing.FakeResponse;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link google.registry.tools.server.DownloadServiceAccountCredentialAction}. */
@RunWith(JUnit4.class)
public class DownloadServiceAccountCredentialActionTest {

  private final DownloadServiceAccountCredentialAction action =
      new DownloadServiceAccountCredentialAction();
  private final FakeResponse response = new FakeResponse();
  private final Function<String, String> encryptedDataRetriever = input -> input + "_mohaha";

  @Before
  public void setUp() {
    action.response = response;
    action.encryptedDataRetriever = encryptedDataRetriever;
  }

  @Test
  public void testSuccess_returnServiceAccountCredential() {
    action.run();
    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_BINARY);
    assertThat(new String(BaseEncoding.base64().decode(response.getPayload()), UTF_8))
        .isEqualTo("json-credential-string_mohaha");
  }

  @Test
  public void testFailure_cannotGetEncryptedCredential() {
    action.encryptedDataRetriever =
        input -> {
          throw new RuntimeException("Something went wrong.");
        };
    action.run();
    assertThat(response.getStatus()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.getContentType()).isEqualTo(MediaType.HTML_UTF_8);
    assertThat(response.getPayload()).isEqualTo("Something went wrong.");
  }
}
