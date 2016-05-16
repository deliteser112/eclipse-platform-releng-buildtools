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

package google.registry.tools;

import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableSet;

import com.beust.jcommander.ParameterException;
import com.googlecode.objectify.Ref;

import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.registrar.Registrar;

import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link UniformRapidSuspensionCommand}. */
public class UniformRapidSuspensionCommandTest
    extends EppToolCommandTestCase<UniformRapidSuspensionCommand> {

  @Before
  public void initRegistrar() {
    // Since the command's history client ID must be CharlestonRoad, resave TheRegistrar that way.
    persistResource(Registrar.loadByClientId("TheRegistrar").asBuilder()
        .setClientIdentifier("CharlestonRoad")
        .build());
  }

  private void persistDomainWithHosts(HostResource... hosts) {
    ImmutableSet.Builder<Ref<HostResource>> hostRefs = new ImmutableSet.Builder<>();
    for (HostResource host : hosts) {
      hostRefs.add(Ref.create(host));
    }
    persistResource(newDomainResource("evil.tld").asBuilder()
        .setNameservers(hostRefs.build())
        .build());
  }

  @Test
  public void testCommand_addsLocksReplacesHostsPrintsUndo() throws Exception {
    persistActiveHost("urs1.example.com");
    persistActiveHost("urs2.example.com");
    persistDomainWithHosts(
        persistActiveHost("ns1.example.com"),
        persistActiveHost("ns2.example.com"));
    runCommandForced("--domain_name=evil.tld", "--hosts=urs1.example.com,urs2.example.com");
    eppVerifier()
        .setClientIdentifier("CharlestonRoad")
        .asSuperuser()
        .verifySent("testdata/uniform_rapid_suspension.xml");
    assertInStdout("uniform_rapid_suspension "
        + "--undo "
        + "--domain_name evil.tld "
        + "--hosts ns1.example.com,ns2.example.com");
  }

  @Test
  public void testCommand_respectsExistingHost() throws Exception {
    persistActiveHost("urs1.example.com");
    persistDomainWithHosts(
        persistActiveHost("urs2.example.com"),
        persistActiveHost("ns1.example.com"));
    runCommandForced("--domain_name=evil.tld", "--hosts=urs1.example.com,urs2.example.com");
    eppVerifier()
        .setClientIdentifier("CharlestonRoad")
        .asSuperuser()
        .verifySent("testdata/uniform_rapid_suspension_existing_host.xml");
    assertInStdout("uniform_rapid_suspension "
        + "--undo "
        + "--domain_name evil.tld "
        + "--hosts ns1.example.com,urs2.example.com");
  }

  @Test
  public void testCommand_generatesUndoForUndelegatedDomain() throws Exception {
    persistActiveHost("urs1.example.com");
    persistActiveHost("urs2.example.com");
    persistActiveDomain("evil.tld");
    runCommandForced("--domain_name=evil.tld", "--hosts=urs1.example.com,urs2.example.com");
    assertInStdout("uniform_rapid_suspension --undo --domain_name evil.tld");
  }

  @Test
  public void testCommand_generatesUndoWithPreserve() throws Exception {
    persistResource(
        newDomainResource("evil.tld").asBuilder()
          .addStatusValue(StatusValue.SERVER_DELETE_PROHIBITED)
          .build());
    runCommandForced("--domain_name=evil.tld");
    assertInStdout(
        "uniform_rapid_suspension --undo --domain_name evil.tld --preserve serverDeleteProhibited");
  }

  @Test
  public void testUndo_removesLocksReplacesHosts() throws Exception {
    persistActiveHost("ns1.example.com");
    persistActiveHost("ns2.example.com");
    persistDomainWithHosts(
        persistActiveHost("urs1.example.com"),
        persistActiveHost("urs2.example.com"));
    runCommandForced(
        "--domain_name=evil.tld", "--undo", "--hosts=ns1.example.com,ns2.example.com");
    eppVerifier()
        .setClientIdentifier("CharlestonRoad")
        .asSuperuser()
        .verifySent("testdata/uniform_rapid_suspension_undo.xml");
    assertNotInStdout("--undo");  // Undo shouldn't print a new undo command.
  }

  @Test
  public void testUndo_respectsPreserveFlag() throws Exception {
    persistActiveHost("ns1.example.com");
    persistActiveHost("ns2.example.com");
    persistDomainWithHosts(
        persistActiveHost("urs1.example.com"),
        persistActiveHost("urs2.example.com"));
    runCommandForced(
        "--domain_name=evil.tld",
        "--undo",
        "--preserve=serverDeleteProhibited",
        "--hosts=ns1.example.com,ns2.example.com");
    eppVerifier()
        .setClientIdentifier("CharlestonRoad")
        .asSuperuser()
        .verifySent("testdata/uniform_rapid_suspension_undo_preserve.xml");
    assertNotInStdout("--undo");  // Undo shouldn't print a new undo command.
  }

  @Test
  public void testFailure_preserveWithoutUndo() throws Exception {
    persistActiveDomain("evil.tld");
    thrown.expect(IllegalArgumentException.class, "--undo");
    runCommandForced("--domain_name=evil.tld", "--preserve=serverDeleteProhibited");
  }

  @Test
  public void testFailure_domainNameRequired() throws Exception {
    persistActiveHost("urs1.example.com");
    persistActiveHost("urs2.example.com");
    persistActiveDomain("evil.tld");
    thrown.expect(ParameterException.class, "--domain_name");
    runCommandForced("--hosts=urs1.example.com,urs2.example.com");
  }
}
