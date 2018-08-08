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
// limitations under the License.package google.registry.flows;

package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.flows.EppXmlSanitizer.sanitizeEppXml;
import static google.registry.testing.TestDataHelper.loadBytes;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import google.registry.testing.EppLoader;
import java.util.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link EppXmlSanitizer}. */
@RunWith(JUnit4.class)
public class EppXmlSanitizerTest {

  private static final String XML_HEADER = "<?xml version=\"1.0\" ?>";

  @Test
  public void testSanitize_noSensitiveData_noop() throws Exception {
    byte[] inputXmlBytes = loadBytes(getClass(), "host_create.xml").read();
    String expectedXml = XML_HEADER + new String(inputXmlBytes, UTF_8);

    String sanitizedXml = sanitizeEppXml(inputXmlBytes);
    assertThat(sanitizedXml).isEqualTo(expectedXml);
  }

  @Test
  public void testSanitize_loginPasswords_sanitized() {
    String inputXml =
        new EppLoader(
                this,
                "login_update_password.xml",
                ImmutableMap.of("PW", "oldpass", "NEWPW", "newPw"))
            .getEppXml();
    String expectedXml =
        XML_HEADER
            + new EppLoader(
                    this,
                    "login_update_password.xml",
                    ImmutableMap.of("PW", "*******", "NEWPW", "*****"))
                .getEppXml();

    String sanitizedXml = sanitizeEppXml(inputXml.getBytes(UTF_8));
    assertThat(sanitizedXml).isEqualTo(expectedXml);
  }

  @Test
  public void testSanitize_loginPasswordTagWrongCase_sanitized() {
    String inputXml =
        new EppLoader(
                this, "login_wrong_case.xml", ImmutableMap.of("PW", "oldpass", "NEWPW", "newPw"))
            .getEppXml();
    String expectedXml =
        XML_HEADER
            + new EppLoader(
                    this,
                    "login_wrong_case.xml",
                    ImmutableMap.of("PW", "*******", "NEWPW", "*****"))
                .getEppXml();

    String sanitizedXml = sanitizeEppXml(inputXml.getBytes(UTF_8));
    assertThat(sanitizedXml).isEqualTo(expectedXml);
  }

  @Test
  public void testSanitize_contactAuthInfo_sanitized() throws Exception {
    byte[] inputXmlBytes = loadBytes(getClass(), "contact_info.xml").read();
    String expectedXml =
        XML_HEADER
            + new EppLoader(this, "contact_info_sanitized.xml", ImmutableMap.of()).getEppXml();

    String sanitizedXml = sanitizeEppXml(inputXmlBytes);
    assertThat(sanitizedXml).isEqualTo(expectedXml);
  }

  @Test
  public void testSanitize_contactCreateResponseAuthInfo_sanitized() throws Exception {
    byte[] inputXmlBytes = loadBytes(getClass(), "contact_info_from_create_response.xml").read();
    String expectedXml =
        XML_HEADER
            + new EppLoader(
                    this, "contact_info_from_create_response_sanitized.xml", ImmutableMap.of())
                .getEppXml();

    String sanitizedXml = sanitizeEppXml(inputXmlBytes);
    assertThat(sanitizedXml).isEqualTo(expectedXml);
  }

  @Test
  public void testSanitize_emptyElement_transformedToLongForm() {
    byte[] inputXmlBytes = "<pw/>".getBytes(UTF_8);
    assertThat(sanitizeEppXml(inputXmlBytes)).isEqualTo(XML_HEADER + "<pw></pw>\n");
  }

  @Test
  public void testSanitize_invalidXML_throws() {
    byte[] inputXmlBytes = "<pw>".getBytes(UTF_8);
    assertThat(sanitizeEppXml(inputXmlBytes))
        .isEqualTo(Base64.getMimeEncoder().encodeToString(inputXmlBytes));
  }

  @Test
  public void testSanitize_unicode_hasCorrectCharCount() {
    byte[] inputXmlBytes = "<pw>\u007F\u4E43x</pw>".getBytes(UTF_8);
    String expectedXml = XML_HEADER + "<pw>C**</pw>\n";
    assertThat(sanitizeEppXml(inputXmlBytes)).isEqualTo(expectedXml);
  }
}
