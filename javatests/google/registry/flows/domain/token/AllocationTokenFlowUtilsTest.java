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

package google.registry.flows.domain.token;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.EppExceptionSubject.assertAboutEppExceptions;
import static google.registry.testing.JUnitBackports.expectThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.domain.token.AllocationTokenFlowUtils.InvalidAllocationTokenException;
import google.registry.model.domain.AllocationToken;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ShardableTestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AllocationTokenFlowUtils}. */
@RunWith(JUnit4.class)
public class AllocationTokenFlowUtilsTest extends ShardableTestCase {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Before
  public void initTest() {
    createTld("tld");
  }

  @Test
  public void test_verifyToken_successfullyVerifiesValidToken() throws Exception {
    AllocationToken token =
        persistResource(new AllocationToken.Builder().setToken("tokeN").build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new AllocationTokenCustomLogic());
    assertThat(
            flowUtils.verifyToken(
                InternetDomainName.from("blah.tld"), "tokeN", Registry.get("tld"), "TheRegistrar"))
        .isEqualTo(token);
  }

  @Test
  public void test_verifyToken_failsOnNonexistentToken() throws Exception {
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new AllocationTokenCustomLogic());
    EppException thrown =
        expectThrows(
            InvalidAllocationTokenException.class,
            () ->
                flowUtils.verifyToken(
                    InternetDomainName.from("blah.tld"),
                    "tokeN",
                    Registry.get("tld"),
                    "TheRegistrar"));
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void test_verifyToken_callsCustomLogic() throws Exception {
    persistResource(new AllocationToken.Builder().setToken("tokeN").build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new FailingAllocationTokenCustomLogic());
    Exception thrown =
        expectThrows(
            IllegalStateException.class,
            () ->
                flowUtils.verifyToken(
                    InternetDomainName.from("blah.tld"),
                    "tokeN",
                    Registry.get("tld"),
                    "TheRegistrar"));
    assertThat(thrown).hasMessageThat().isEqualTo("failed for tests");
  }

  @Test
  public void test_checkDomainsWithToken_successfullyVerifiesValidToken() throws Exception {
    persistResource(new AllocationToken.Builder().setToken("tokeN").build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new AllocationTokenCustomLogic());
    assertThat(
        flowUtils.checkDomainsWithToken(
            ImmutableList.of("blah.tld", "blah2.tld"), "tokeN", "TheRegistrar"))
        .containsExactlyEntriesIn(ImmutableMap.of("blah.tld", "", "blah2.tld", ""));
  }

  @Test
  public void test_checkDomainsWithToken_showsFailureMessageForRedeemedToken() throws Exception {
    persistResource(
        new AllocationToken.Builder()
            .setToken("tokeN")
            .setRedemptionHistoryEntry(Key.create(HistoryEntry.class, 101L))
            .build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new AllocationTokenCustomLogic());
    assertThat(
            flowUtils.checkDomainsWithToken(
                ImmutableList.of("blah.tld", "blah2.tld"), "tokeN", "TheRegistrar"))
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                "blah.tld",
                "Alloc token was already redeemed",
                "blah2.tld",
                "Alloc token was already redeemed"));
  }

  @Test
  public void test_checkDomainsWithToken_callsCustomLogic() throws Exception {
    persistResource(new AllocationToken.Builder().setToken("tokeN").build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new FailingAllocationTokenCustomLogic());
    Exception thrown =
        expectThrows(
            IllegalStateException.class,
            () ->
                flowUtils.checkDomainsWithToken(
                    ImmutableList.of("blah.tld", "blah2.tld"), "tokeN", "TheRegistrar"));
    assertThat(thrown).hasMessageThat().isEqualTo("failed for tests");
  }

  /** An {@link AllocationTokenCustomLogic} class that throws exceptions on every method. */
  private static class FailingAllocationTokenCustomLogic extends AllocationTokenCustomLogic {

    @Override
    public AllocationToken verifyToken(
        InternetDomainName domainName, AllocationToken token, Registry registry, String clientId)
        throws EppException {
      throw new IllegalStateException("failed for tests");
    }

    @Override
    public ImmutableMap<String, String> checkDomainsWithToken(
        ImmutableMap<String, String> checkResults, AllocationToken tokenEntity, String clientId) {
      throw new IllegalStateException("failed for tests");
    }
  }
}
