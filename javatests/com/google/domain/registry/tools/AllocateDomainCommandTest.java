// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.tools;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.flows.EppServletUtils.APPLICATION_EPP_XML_UTF8;
import static com.google.domain.registry.flows.FlowRegistry.getFlowClass;
import static com.google.domain.registry.model.domain.DesignatedContact.Type.ADMIN;
import static com.google.domain.registry.model.domain.DesignatedContact.Type.BILLING;
import static com.google.domain.registry.model.domain.DesignatedContact.Type.TECH;
import static com.google.domain.registry.model.domain.launch.ApplicationStatus.VALIDATED;
import static com.google.domain.registry.model.registry.Registry.TldState.QUIET_PERIOD;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainApplication;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static com.google.domain.registry.util.ResourceUtils.readResourceUtf8;
import static com.google.domain.registry.xml.XmlTestUtils.assertXmlEquals;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.flows.EppXmlTransformer;
import com.google.domain.registry.flows.domain.DomainAllocateFlow;
import com.google.domain.registry.model.domain.DesignatedContact;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.domain.launch.LaunchNotice;
import com.google.domain.registry.model.domain.secdns.DelegationSignerData;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.eppinput.EppInput;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.tools.ServerSideCommand.Connection;

import com.beust.jcommander.ParameterException;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.io.IOException;
import java.util.List;

/** Unit tests for {@link AllocateDomainCommand}. */
public class AllocateDomainCommandTest extends CommandTestCase<AllocateDomainCommand> {

  private static final String EXPECTED_XML_ONE =
      readResourceUtf8(AllocateDomainCommandTest.class, "testdata/allocate_domain.xml");
  private static final String EXPECTED_XML_TWO =
      readResourceUtf8(AllocateDomainCommandTest.class, "testdata/allocate_domain2.xml");

  @Mock
  Connection connection;

  @Captor
  ArgumentCaptor<byte[]> xml;

  @Before
  public void init() throws IOException {
    command.setConnection(connection);
    createTld("tld", QUIET_PERIOD);
    createApplication("example-one.tld", "testdata/domain_create_sunrush.xml", "1-TLD");
    createApplication("example-two.tld", "testdata/domain_create_sunrush2.xml", "2-TLD");
  }

  private void createApplication(String name, String xmlFile, String repoId) throws IOException {
    DomainApplication application =
        persistResource(newDomainApplication(name)
            .asBuilder()
            .setRepoId(repoId)
            .setCreationTimeForTest(START_OF_TIME)
            .setRegistrant(ReferenceUnion.create(persistActiveContact("registrant")))
            .setContacts(ImmutableSet.of(
                DesignatedContact.create(
                    ADMIN,
                    ReferenceUnion.create(persistActiveContact("adminContact"))),
                DesignatedContact.create(
                    BILLING,
                    ReferenceUnion.create(persistActiveContact("billingContact"))),
                DesignatedContact.create(
                    TECH,
                    ReferenceUnion.create(persistActiveContact("techContact")))))
            .setNameservers(ImmutableSet.of(
                ReferenceUnion.create(persistActiveHost("ns1.example.com")),
                ReferenceUnion.create(persistActiveHost("ns2.example.com"))))
            .setApplicationStatus(VALIDATED)
            .setDsData(ImmutableSet.of(
                DelegationSignerData.create(
                    12345, 3, 1, base16().decode("49FD46E6C4B45C55D4AC")),
                DelegationSignerData.create(
                    56789, 2, 4, base16().decode("69FD46E6C4A45C55D4AC"))))
            .setLaunchNotice(LaunchNotice.create(
                "370d0b7c9223372036854775807",
                "tmch",
                DateTime.parse("2010-08-16T09:00:00.0Z"),
                DateTime.parse("2009-08-16T09:00:00.0Z")))
            .build());

    persistResource(
        new HistoryEntry.Builder()
            .setParent(application)
            .setClientId("NewRegistrar")
            .setModificationTime(application.getCreationTime())
            .setTrid(Trid.create("ABC-123"))
            .setXmlBytes(toByteArray(getResource(AllocateDomainCommandTest.class, xmlFile)))
            .build());
  }

  private void verifySent(boolean dryRun, String clientId, String... expectedXml) throws Exception {
    ImmutableMap<String, ?> params = ImmutableMap.of(
        "dryRun", dryRun,
        "clientIdentifier", clientId,
        "superuser", true);
    verify(connection, times(expectedXml.length))
        .send(eq("/_dr/epptool"), eq(params), eq(APPLICATION_EPP_XML_UTF8), xml.capture());

    List<byte[]> allCapturedXml = xml.getAllValues();
    assertThat(allCapturedXml).hasSize(expectedXml.length);
    int capturedXmlIndex = 0;
    for (String expected : expectedXml) {
      assertXmlEquals(expected, new String(allCapturedXml.get(capturedXmlIndex++), UTF_8));
    }
  }

  @Test
  public void testSuccess() throws Exception {
    runCommand("--ids=1-TLD", "--force", "--superuser");
    // NB: These commands are all sent on behalf of the sponsoring registrar, in this case
    // "TheRegistrar".
    verifySent(false, "TheRegistrar", EXPECTED_XML_ONE);
  }

  @Test
  public void testSuccess_multiple() throws Exception {
    runCommand("--ids=1-TLD,2-TLD", "--force", "--superuser");
    verifySent(false, "TheRegistrar", EXPECTED_XML_ONE, EXPECTED_XML_TWO);
  }

  @Test
  public void testSuccess_dryRun() throws Exception {
    runCommand("--ids=1-TLD", "--dry_run", "--superuser");
    verifySent(true, "TheRegistrar", EXPECTED_XML_ONE);
  }

  @Test
  public void testFailure_notAsSuperuser() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--ids=1-TLD", "--force");
  }

  @Test
  public void testFailure_forceAndDryRunIncompatible() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--ids=1-TLD", "--force", "--dry_run", "--superuser");
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--ids=1-TLD", "--force", "--unrecognized=foo", "--superuser");
  }

  @Test
  public void testXmlInstantiatesFlow() throws Exception {
    assertThat(
        getFlowClass(EppXmlTransformer.<EppInput>unmarshal(EXPECTED_XML_ONE.getBytes(UTF_8))))
            .isEqualTo(DomainAllocateFlow.class);
  }
}
