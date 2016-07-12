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

package google.registry.ui.server.registrar;

import static com.google.common.base.Strings.repeat;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.security.JsonHttpTestUtils.createJsonPayload;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for security_settings.js use of {@link RegistrarServlet}.
 *
 * <p>The default read and session validation tests are handled by the superclass.
 */
@RunWith(MockitoJUnitRunner.class)
public class WhoisSettingsTest extends RegistrarServletTestCase {

  @Test
  public void testPost_update_success() throws Exception {
    Registrar modified = Registrar.loadByClientId(CLIENT_ID).asBuilder()
        .setEmailAddress("hello.kitty@example.com")
        .setPhoneNumber("+1.2125650000")
        .setFaxNumber("+1.2125650001")
        .setReferralUrl("http://acme.com/")
        .setWhoisServer("ns1.foo.bar")
        .setLocalizedAddress(new RegistrarAddress.Builder()
            .setStreet(ImmutableList.of("76 Ninth Avenue", "Eleventh Floor"))
            .setCity("New York")
            .setState("NY")
            .setZip("10009")
            .setCountryCode("US")
            .build())
        .build();
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", modified.toJsonMap())));
    servlet.service(req, rsp);
    assertThat(json.get().get("status")).isEqualTo("SUCCESS");
    assertThat(json.get().get("results")).isEqualTo(asList(modified.toJsonMap()));
    assertThat(Registrar.loadByClientId(CLIENT_ID)).isEqualTo(modified);
  }

  @Test
  public void testPost_badUsStateCode_returnsFormFieldError() throws Exception {
    Registrar modified = Registrar.loadByClientId(CLIENT_ID).asBuilder()
        .setEmailAddress("hello.kitty@example.com")
        .setPhoneNumber("+1.2125650000")
        .setFaxNumber("+1.2125650001")
        .setLocalizedAddress(new RegistrarAddress.Builder()
            .setStreet(ImmutableList.of("76 Ninth Avenue", "Eleventh Floor"))
            .setCity("New York")
            .setState("ZZ")
            .setZip("10009")
            .setCountryCode("US")
            .build())
        .build();
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", modified.toJsonMap())));
    servlet.service(req, rsp);
    assertThat(json.get().get("status")).isEqualTo("ERROR");
    assertThat(json.get().get("field")).isEqualTo("localizedAddress.state");
    assertThat(json.get().get("message")).isEqualTo("Unknown US state code.");
    assertThat(Registrar.loadByClientId(CLIENT_ID)).isNotEqualTo(modified);
  }

  @Test
  public void testPost_badAddress_returnsFormFieldError() throws Exception {
    Registrar modified = Registrar.loadByClientId(CLIENT_ID).asBuilder()
        .setEmailAddress("hello.kitty@example.com")
        .setPhoneNumber("+1.2125650000")
        .setFaxNumber("+1.2125650001")
        .setLocalizedAddress(new RegistrarAddress.Builder()
            .setStreet(ImmutableList.of("76 Ninth Avenue", repeat("lol", 200)))
            .setCity("New York")
            .setState("NY")
            .setZip("10009")
            .setCountryCode("US")
            .build())
        .build();
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", modified.toJsonMap())));
    servlet.service(req, rsp);
    assertThat(json.get().get("status")).isEqualTo("ERROR");
    assertThat(json.get().get("field")).isEqualTo("localizedAddress.street[1]");
    assertThat((String) json.get().get("message"))
        .contains("Number of characters (600) not in range");
    assertThat(Registrar.loadByClientId(CLIENT_ID)).isNotEqualTo(modified);
  }

  @Test
  public void testPost_badWhoisServer_returnsFormFieldError() throws Exception {
    Registrar modified = Registrar.loadByClientId(CLIENT_ID).asBuilder()
        .setWhoisServer("tears@dry.tragical.lol")
        .build();
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", modified.toJsonMap())));
    servlet.service(req, rsp);
    assertThat(json.get().get("status")).isEqualTo("ERROR");
    assertThat(json.get().get("field")).isEqualTo("whoisServer");
    assertThat(json.get().get("message")).isEqualTo("Not a valid hostname.");
    assertThat(Registrar.loadByClientId(CLIENT_ID)).isNotEqualTo(modified);
  }
}
