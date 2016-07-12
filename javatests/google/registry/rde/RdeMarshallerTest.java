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

package google.registry.rde;

import static com.google.common.truth.Truth.assertThat;

import google.registry.model.registrar.Registrar;
import google.registry.testing.AppEngineRule;
import google.registry.xml.XmlTestUtils;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdeMarshaller}. */
@RunWith(JUnit4.class)
public class RdeMarshallerTest {

  private static final String DECLARATION =
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Test
  public void testMarshalRegistrar_validData_producesXmlFragment() throws Exception {
    DepositFragment fragment =
        new RdeMarshaller().marshalRegistrar(Registrar.loadByClientId("TheRegistrar"));
    assertThat(fragment.type()).isEqualTo(RdeResourceType.REGISTRAR);
    assertThat(fragment.error()).isEmpty();
    String expected = ""
        + "<rdeRegistrar:registrar>\n"
        + "    <rdeRegistrar:id>TheRegistrar</rdeRegistrar:id>\n"
        + "    <rdeRegistrar:name>The Registrar</rdeRegistrar:name>\n"
        + "    <rdeRegistrar:gurid>1</rdeRegistrar:gurid>\n"
        + "    <rdeRegistrar:status>ok</rdeRegistrar:status>\n"
        + "    <rdeRegistrar:postalInfo type=\"loc\">\n"
        + "        <rdeRegistrar:addr>\n"
        + "            <rdeRegistrar:street>123 Example Bőulevard</rdeRegistrar:street>\n"
        + "            <rdeRegistrar:city>Williamsburg</rdeRegistrar:city>\n"
        + "            <rdeRegistrar:sp>NY</rdeRegistrar:sp>\n"
        + "            <rdeRegistrar:pc>11211</rdeRegistrar:pc>\n"
        + "            <rdeRegistrar:cc>US</rdeRegistrar:cc>\n"
        + "        </rdeRegistrar:addr>\n"
        + "    </rdeRegistrar:postalInfo>\n"
        + "    <rdeRegistrar:postalInfo type=\"int\">\n"
        + "        <rdeRegistrar:addr>\n"
        + "            <rdeRegistrar:street>123 Example Boulevard</rdeRegistrar:street>\n"
        + "            <rdeRegistrar:city>Williamsburg</rdeRegistrar:city>\n"
        + "            <rdeRegistrar:sp>NY</rdeRegistrar:sp>\n"
        + "            <rdeRegistrar:pc>11211</rdeRegistrar:pc>\n"
        + "            <rdeRegistrar:cc>US</rdeRegistrar:cc>\n"
        + "        </rdeRegistrar:addr>\n"
        + "    </rdeRegistrar:postalInfo>\n"
        + "    <rdeRegistrar:voice>+1.2223334444</rdeRegistrar:voice>\n"
        + "    <rdeRegistrar:email>new.registrar@example.com</rdeRegistrar:email>\n"
        + "    <rdeRegistrar:whoisInfo>\n"
        + "        <rdeRegistrar:name>whois.nic.fakewhois.example</rdeRegistrar:name>\n"
        + "    </rdeRegistrar:whoisInfo>\n"
        + "    <rdeRegistrar:crDate>mine eyes have seen the glory</rdeRegistrar:crDate>\n"
        + "    <rdeRegistrar:upDate>of the coming of the borg</rdeRegistrar:upDate>\n"
        + "</rdeRegistrar:registrar>\n";
    XmlTestUtils.assertXmlEquals(DECLARATION + expected, DECLARATION + fragment.xml(),
        "registrar.crDate",
        "registrar.upDate");
  }

  @Test
  public void testMarshalRegistrar_breaksRdeXmlSchema_producesErrorMessage() throws Exception {
    Registrar reg = Registrar.loadByClientId("TheRegistrar").asBuilder()
        .setLocalizedAddress(null)
        .setInternationalizedAddress(null)
        .build();
    DepositFragment fragment = new RdeMarshaller().marshalRegistrar(reg);
    assertThat(fragment.type()).isEqualTo(RdeResourceType.REGISTRAR);
    assertThat(fragment.xml()).isEmpty();
    assertThat(fragment.error()).isEqualTo(""
        + "RDE XML schema validation failed: "
            + "Key<?>(EntityGroupRoot(\"cross-tld\")/Registrar(\"TheRegistrar\"))\n"
        + "org.xml.sax.SAXParseException; lineNumber: 0; columnNumber: 0; cvc-complex-type.2.4.a: "
            + "Invalid content was found starting with element 'rdeRegistrar:voice'. "
            + "One of '{\"urn:ietf:params:xml:ns:rdeRegistrar-1.0\":postalInfo}' is expected.\n"
        + "<rdeRegistrar:registrar>\n"
        + "    <rdeRegistrar:id>TheRegistrar</rdeRegistrar:id>\n"
        + "    <rdeRegistrar:name>The Registrar</rdeRegistrar:name>\n"
        + "    <rdeRegistrar:gurid>1</rdeRegistrar:gurid>\n"
        + "    <rdeRegistrar:status>ok</rdeRegistrar:status>\n"
        + "    <rdeRegistrar:voice>+1.2223334444</rdeRegistrar:voice>\n"
        + "    <rdeRegistrar:email>new.registrar@example.com</rdeRegistrar:email>\n"
        + "    <rdeRegistrar:whoisInfo>\n"
        + "        <rdeRegistrar:name>whois.nic.fakewhois.example</rdeRegistrar:name>\n"
        + "    </rdeRegistrar:whoisInfo>\n"
        + "    <rdeRegistrar:crDate>" + ft(reg.getCreationTime()) + "</rdeRegistrar:crDate>\n"
        + "    <rdeRegistrar:upDate>" + ft(reg.getLastUpdateTime()) + "</rdeRegistrar:upDate>\n"
        + "</rdeRegistrar:registrar>\n"
        + "\n");
  }

  @Test
  public void testMarshalRegistrar_unicodeCharacters_dontGetMangled() throws Exception {
    DepositFragment fragment =
        new RdeMarshaller().marshalRegistrar(Registrar.loadByClientId("TheRegistrar"));
    assertThat(fragment.xml()).contains("123 Example Bőulevard");
  }

  /** Formats {@code timestamp} without milliseconds. */
  private static String ft(DateTime timestamp) {
    return ISODateTimeFormat.dateTimeNoMillis().withZoneUTC().print(timestamp);
  }
}
