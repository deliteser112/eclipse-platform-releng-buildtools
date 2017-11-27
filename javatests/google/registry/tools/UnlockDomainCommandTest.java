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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.eppcommon.StatusValue.SERVER_DELETE_PROHIBITED;
import static google.registry.model.eppcommon.StatusValue.SERVER_TRANSFER_PROHIBITED;
import static google.registry.model.eppcommon.StatusValue.SERVER_UPDATE_PROHIBITED;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.expectThrows;
import static google.registry.tools.server.ToolsTestData.loadUtf8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Unit tests for {@link UnlockDomainCommand}. */
public class UnlockDomainCommandTest extends EppToolCommandTestCase<UnlockDomainCommand> {

  /** Gets an overridden eppVerifier that has superuser set to true on it. */
  @Override
  EppToolVerifier eppVerifier() {
    return new EppToolVerifier()
        .withConnection(connection)
        .withClientId("NewRegistrar")
        .asSuperuser();
  }

  private static void persistLockedDomain(String domainName) {
    persistResource(
        newDomainResource(domainName)
            .asBuilder()
            .addStatusValues(
                ImmutableSet.of(
                    SERVER_DELETE_PROHIBITED, SERVER_TRANSFER_PROHIBITED, SERVER_UPDATE_PROHIBITED))
            .build());
  }

  @Test
  public void testSuccess_sendsCorrectEppXml() throws Exception {
    persistLockedDomain("example.tld");
    runCommandForced("--client=NewRegistrar", "example.tld");
    eppVerifier()
        .verifySentContents(
            ImmutableList.of(
                loadUtf8("domain_unlock.xml", ImmutableMap.of("DOMAIN", "example.tld"))));
  }

  @Test
  public void testSuccess_partiallyUpdatesStatuses() throws Exception {
    persistResource(
        newDomainResource("example.tld")
            .asBuilder()
            .addStatusValues(ImmutableSet.of(SERVER_DELETE_PROHIBITED, SERVER_UPDATE_PROHIBITED))
            .build());
    runCommandForced("--client=NewRegistrar", "example.tld");
    eppVerifier().verifySent("domain_unlock_partial_statuses.xml");
  }

  @Test
  public void testSuccess_manyDomains() throws Exception {
    List<String> params = new ArrayList<>();
    List<String> expectedXmls = new ArrayList<>();
    params.add("--client=NewRegistrar");
    // Create 26 domains -- one more than the number of entity groups allowed in a transaction (in
    // case that was going to be the failure point).
    for (int n = 0; n < 26; n++) {
      String domain = String.format("domain%d.tld", n);
      persistLockedDomain(domain);
      params.add(domain);
      expectedXmls.add(loadUtf8("domain_unlock.xml", ImmutableMap.of("DOMAIN", domain)));
    }
    runCommandForced(params);
    eppVerifier().verifySentContents(expectedXmls);
  }

  @Test
  public void testFailure_domainDoesntExist() throws Exception {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> runCommandForced("--client=NewRegistrar", "missing.tld"));
    assertThat(e).hasMessageThat().isEqualTo("Domain 'missing.tld' does not exist");
  }

  @Test
  public void testSuccess_alreadyUnlockedDomain_performsNoAction() throws Exception {
    persistActiveDomain("example.tld");
    runCommandForced("--client=NewRegistrar", "example.tld");
    eppVerifier().verifyNothingSent();
  }

  @Test
  public void testFailure_duplicateDomainsAreSpecified() throws Exception {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> runCommandForced("--client=NewRegistrar", "dupe.tld", "dupe.tld"));
    assertThat(e).hasMessageThat().isEqualTo("Duplicate domain arguments found: 'dupe.tld'");
  }
}
