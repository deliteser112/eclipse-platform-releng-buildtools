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

package google.registry.tools;

import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.registrar.Registrar;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link UniformRapidSuspensionCommand}. */
public class UniformRapidSuspensionCommandTest
    extends EppToolCommandTestCase<UniformRapidSuspensionCommand> {

  HostResource ns1;
  HostResource ns2;
  HostResource urs1;
  HostResource urs2;

  @Before
  public void initResources() {
    // Since the command's history client ID must be CharlestonRoad, resave TheRegistrar that way.
    persistResource(Registrar.loadByClientId("TheRegistrar").asBuilder()
        .setClientId("CharlestonRoad")
        .build());
    ns1 = persistActiveHost("ns1.example.com");
    ns2 = persistActiveHost("ns2.example.com");
    urs1 = persistActiveHost("urs1.example.com");
    urs2 = persistActiveHost("urs2.example.com");
  }

  private void persistDomainWithHosts(HostResource... hosts) {
    ImmutableSet.Builder<Key<HostResource>> hostRefs = new ImmutableSet.Builder<>();
    for (HostResource host : hosts) {
      hostRefs.add(Key.create(host));
    }
    persistResource(newDomainResource("evil.tld").asBuilder()
        .setNameservers(hostRefs.build())
        .setDsData(ImmutableSet.of(
            DelegationSignerData.create(1, 2, 3, new HexBinaryAdapter().unmarshal("dead")),
            DelegationSignerData.create(4, 5, 6, new HexBinaryAdapter().unmarshal("beef"))))
        .build());
  }

  @Test
  public void testCommand_addsLocksReplacesHostsAndDsDataPrintsUndo() throws Exception {
    persistDomainWithHosts(ns1, ns2);
    runCommandForced(
        "--domain_name=evil.tld",
        "--hosts=urs1.example.com,urs2.example.com",
        "--dsdata={\"keyTag\":1,\"alg\":1,\"digestType\":1,\"digest\":\"abc\"}");
    eppVerifier()
        .withClientId("CharlestonRoad")
        .asSuperuser()
        .verifySent("uniform_rapid_suspension.xml");
    assertInStdout("uniform_rapid_suspension --undo");
    assertInStdout("--domain_name evil.tld");
    assertInStdout("--hosts ns1.example.com,ns2.example.com");
    assertInStdout("--dsdata "
        + "{\"keyTag\":1,\"algorithm\":2,\"digestType\":3,\"digest\":\"DEAD\"},"
        + "{\"keyTag\":4,\"algorithm\":5,\"digestType\":6,\"digest\":\"BEEF\"}");
    assertNotInStdout("--locks_to_preserve");
  }

  @Test
  public void testCommand_respectsExistingHost() throws Exception {
    persistDomainWithHosts(urs2, ns1);
    runCommandForced("--domain_name=evil.tld", "--hosts=urs1.example.com,urs2.example.com");
    eppVerifier()
        .withClientId("CharlestonRoad")
        .asSuperuser()
        .verifySent("uniform_rapid_suspension_existing_host.xml");
    assertInStdout("uniform_rapid_suspension --undo ");
    assertInStdout("--domain_name evil.tld");
    assertInStdout("--hosts ns1.example.com,urs2.example.com");
    assertNotInStdout("--locks_to_preserve");
  }

  @Test
  public void testCommand_generatesUndoForUndelegatedDomain() throws Exception {
    persistActiveDomain("evil.tld");
    runCommandForced("--domain_name=evil.tld", "--hosts=urs1.example.com,urs2.example.com");
    assertInStdout("uniform_rapid_suspension --undo");
    assertInStdout("--domain_name evil.tld");
    assertNotInStdout("--locks_to_preserve");
  }

  @Test
  public void testCommand_generatesUndoWithLocksToPreserve() throws Exception {
    persistResource(
        newDomainResource("evil.tld").asBuilder()
          .addStatusValue(StatusValue.SERVER_DELETE_PROHIBITED)
          .build());
    runCommandForced("--domain_name=evil.tld");
    assertInStdout("uniform_rapid_suspension --undo");
    assertInStdout("--domain_name evil.tld");
    assertInStdout("--locks_to_preserve serverDeleteProhibited");
  }

  @Test
  public void testUndo_removesLocksReplacesHostsAndDsData() throws Exception {
    persistDomainWithHosts(urs1, urs2);
    runCommandForced(
        "--domain_name=evil.tld", "--undo", "--hosts=ns1.example.com,ns2.example.com");
    eppVerifier()
        .withClientId("CharlestonRoad")
        .asSuperuser()
        .verifySent("uniform_rapid_suspension_undo.xml");
    assertNotInStdout("--undo");  // Undo shouldn't print a new undo command.
  }

  @Test
  public void testUndo_respectsLocksToPreserveFlag() throws Exception {
    persistDomainWithHosts(urs1, urs2);
    runCommandForced(
        "--domain_name=evil.tld",
        "--undo",
        "--locks_to_preserve=serverDeleteProhibited",
        "--hosts=ns1.example.com,ns2.example.com");
    eppVerifier()
        .withClientId("CharlestonRoad")
        .asSuperuser()
        .verifySent("uniform_rapid_suspension_undo_preserve.xml");
    assertNotInStdout("--undo");  // Undo shouldn't print a new undo command.
  }

  @Test
  public void testFailure_locksToPreserveWithoutUndo() throws Exception {
    persistActiveDomain("evil.tld");
    thrown.expect(IllegalArgumentException.class, "--undo");
    runCommandForced("--domain_name=evil.tld", "--locks_to_preserve=serverDeleteProhibited");
  }

  @Test
  public void testFailure_domainNameRequired() throws Exception {
    persistActiveDomain("evil.tld");
    thrown.expect(ParameterException.class, "--domain_name");
    runCommandForced("--hosts=urs1.example.com,urs2.example.com");
  }

  @Test
  public void testFailure_extraFieldInDsData() throws Exception {
    persistActiveDomain("evil.tld");
    thrown.expect(IllegalArgumentException.class, "Incorrect fields on --dsdata JSON");
    runCommandForced(
        "--domain_name=evil.tld",
        "--dsdata={\"keyTag\":1,\"alg\":1,\"digestType\":1,\"digest\":\"abc\",\"foo\":1}");
  }

  @Test
  public void testFailure_missingFieldInDsData() throws Exception {
    persistActiveDomain("evil.tld");
    thrown.expect(IllegalArgumentException.class, "Incorrect fields on --dsdata JSON");
    runCommandForced(
        "--domain_name=evil.tld",
        "--dsdata={\"keyTag\":1,\"alg\":1,\"digestType\":1}");
  }

  @Test
  public void testFailure_malformedDsData() throws Exception {
    persistActiveDomain("evil.tld");
    thrown.expect(IllegalArgumentException.class, "Invalid --dsdata JSON");
    runCommandForced(
        "--domain_name=evil.tld",
        "--dsdata=[1,2,3]");
  }
}
