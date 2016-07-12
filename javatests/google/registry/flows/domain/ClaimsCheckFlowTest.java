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

package google.registry.flows.domain;

import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.flows.ResourceCheckFlow.TooManyResourceChecksException;
import google.registry.flows.ResourceFlow.BadCommandForRegistryPhaseException;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import google.registry.flows.domain.DomainFlowUtils.TldDoesNotExistException;
import google.registry.model.domain.DomainResource;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.testing.DatastoreHelper;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ClaimsCheckFlow}. */
public class ClaimsCheckFlowTest extends ResourceFlowTestCase<ClaimsCheckFlow, DomainResource> {

  public ClaimsCheckFlowTest() {
    setEppInput("domain_check_claims.xml");
  }

  @Before
  public void initCheckTest() {
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().build());
  }

  protected void doSuccessfulTest(String expectedXmlFilename) throws Exception {
    assertTransactionalFlow(false);
    assertNoHistory();  // Checks don't create a history event.
    assertNoBillingEvents();  // Checks are always free.
    runFlowAssertResponse(readFile(expectedXmlFilename));
  }

  @Test
  public void testSuccess_noClaims() throws Exception {
    doSuccessfulTest("domain_check_claims_response_none.xml");
  }

  @Test
  public void testSuccess_sunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistResource(Registry.get("tld").asBuilder().build());
    doSuccessfulTest("domain_check_claims_response_none.xml");
  }

  @Test
  public void testSuccess_quietPeriod() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistResource(Registry.get("tld").asBuilder().build());
    doSuccessfulTest("domain_check_claims_response_none.xml");
  }

  @Test
  public void testSuccess_oneClaim() throws Exception {
    persistClaimsList(
        ImmutableMap.of("example2", "2013041500/2/6/9/rJ1NrDO92vDsAzf7EQzgjX4R0000000001"));
    doSuccessfulTest("domain_check_claims_response.xml");
  }


  @Test
  public void testSuccess_multipleTlds() throws Exception {
    setEppInput("domain_check_claims_multiple_tlds.xml");
    createTld("tld1");
    createTld("tld2");
    persistClaimsList(
        ImmutableMap.of("example", "2013041500/2/6/9/rJ1NrDO92vDsAzf7EQzgjX4R0000000001"));
    doSuccessfulTest("domain_check_claims_response_multiple_tlds.xml");
  }

  @Test
  public void testSuccess_50IdsAllowed() throws Exception {
    // Make sure we don't have a regression that reduces the number of allowed checks.
    setEppInput("domain_check_claims_50.xml");
    runFlow();
  }

  @Test
  public void testFailure_TooManyIds() throws Exception {
    setEppInput("domain_check_claims_51.xml");
    thrown.expect(TooManyResourceChecksException.class);
    runFlow();
  }

  @Test
  public void testFailure_tldDoesntExist() throws Exception {
    setEppInput("domain_check_claims_bad_tld.xml");
    thrown.expect(TldDoesNotExistException.class);
    runFlow();
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    thrown.expect(NotAuthorizedForTldException.class);
    DatastoreHelper.persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    runFlow();
  }

  @Test
  public void testFailure_predelgation() throws Exception {
    createTld("tld", TldState.PREDELEGATION);
    persistResource(Registry.get("tld").asBuilder().build());
    setEppInput("domain_check_claims.xml");
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_sunrise() throws Exception {
    createTld("tld", TldState.SUNRISE);
    persistResource(Registry.get("tld").asBuilder().build());
    setEppInput("domain_check_claims.xml");
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_multipleTlds_oneHasEndedClaims() throws Exception {
    createTld("tld1");
    createTld("tld2");
    persistResource(Registry.get("tld2").asBuilder()
        .setClaimsPeriodEnd(clock.nowUtc().minusMillis(1))
        .build());
    setEppInput("domain_check_claims_multiple_tlds.xml");
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }
}
