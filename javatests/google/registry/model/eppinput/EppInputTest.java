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

package google.registry.model.eppinput;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.util.ResourceUtils.readResourceBytes;

import google.registry.model.contact.ContactResourceTest;
import google.registry.model.domain.DomainResourceTest;
import google.registry.model.eppinput.EppInput.InnerCommand;
import google.registry.model.eppinput.EppInput.Login;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link EppInput}. */
@RunWith(JUnit4.class)
public class EppInputTest {

  @Test
  public void testUnmarshalling_contactInfo() throws Exception {
    EppInput input =
        unmarshal(
            EppInput.class,
            readResourceBytes(ContactResourceTest.class, "testdata/contact_info.xml").read());
    assertThat(input.getTargetIds()).containsExactly("sh8013");
    assertThat(input.getCommandWrapper().getClTrid()).isEqualTo("ABC-12345");
    assertThat(input.getCommandName()).isEqualTo("Info");
  }

  @Test
  public void testUnmarshalling_domainCheck() throws Exception {
    EppInput input =
        unmarshal(
            EppInput.class,
            readResourceBytes(DomainResourceTest.class, "testdata/domain_check.xml").read());
    assertThat(input.getTargetIds()).containsExactly("example.com", "example.net", "example.org");
    assertThat(input.getCommandWrapper().getClTrid()).isEqualTo("ABC-12345");
    assertThat(input.getCommandName()).isEqualTo("Check");
  }

  @Test
  public void testUnmarshalling_login() throws Exception {
    EppInput input =
        unmarshal(EppInput.class, readResourceBytes(getClass(), "testdata/login_valid.xml").read());
    assertThat(input.getTargetIds()).isEmpty();
    assertThat(input.getCommandWrapper().getClTrid()).isEqualTo("ABC-12345");
    assertThat(input.getCommandName()).isEqualTo("Login");
    InnerCommand command = input.getCommandWrapper().getCommand();
    assertThat(command).isInstanceOf(Login.class);
    Login loginCommand = (Login) command;
    assertThat(loginCommand.clientId).isEqualTo("NewRegistrar");
    assertThat(loginCommand.password).isEqualTo("foo-BAR2");
    assertThat(loginCommand.newPassword).isNull();
    assertThat(loginCommand.options.version).isEqualTo("1.0");
    assertThat(loginCommand.options.language).isEqualTo("en");
    assertThat(loginCommand.services.objectServices)
        .containsExactly(
            "urn:ietf:params:xml:ns:host-1.0",
            "urn:ietf:params:xml:ns:domain-1.0",
            "urn:ietf:params:xml:ns:contact-1.0");
    assertThat(loginCommand.services.serviceExtensions)
        .containsExactly("urn:ietf:params:xml:ns:launch-1.0", "urn:ietf:params:xml:ns:rgp-1.0");
  }
}

