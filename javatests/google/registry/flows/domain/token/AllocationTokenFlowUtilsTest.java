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
import static google.registry.model.domain.token.AllocationToken.TokenType.SINGLE_USE;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.EppExceptionSubject.assertAboutEppExceptions;
import static google.registry.testing.JUnitBackports.assertThrows;
import static org.joda.time.DateTimeZone.UTC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.domain.token.AllocationTokenFlowUtils.InvalidAllocationTokenException;
import google.registry.model.domain.DomainCommand;
import google.registry.model.domain.token.AllocationToken;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ShardableTestCase;
import java.util.Map;
import org.joda.time.DateTime;
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
        persistResource(
            new AllocationToken.Builder().setToken("tokeN").setTokenType(SINGLE_USE).build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new AllocationTokenCustomLogic());
    assertThat(
            flowUtils.verifyToken(
                createCommand("blah.tld"),
                "tokeN",
                Registry.get("tld"),
                "TheRegistrar",
                DateTime.now(UTC)))
        .isEqualTo(token);
  }

  @Test
  public void test_verifyToken_failsOnNonexistentToken() {
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new AllocationTokenCustomLogic());
    EppException thrown =
        assertThrows(
            InvalidAllocationTokenException.class,
            () ->
                flowUtils.verifyToken(
                    createCommand("blah.tld"),
                    "tokeN",
                    Registry.get("tld"),
                    "TheRegistrar",
                    DateTime.now(UTC)));
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void test_verifyToken_callsCustomLogic() {
    persistResource(
        new AllocationToken.Builder().setToken("tokeN").setTokenType(SINGLE_USE).build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new FailingAllocationTokenCustomLogic());
    Exception thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                flowUtils.verifyToken(
                    createCommand("blah.tld"),
                    "tokeN",
                    Registry.get("tld"),
                    "TheRegistrar",
                    DateTime.now(UTC)));
    assertThat(thrown).hasMessageThat().isEqualTo("failed for tests");
  }

  @Test
  public void test_checkDomainsWithToken_successfullyVerifiesValidToken() {
    persistResource(
        new AllocationToken.Builder().setToken("tokeN").setTokenType(SINGLE_USE).build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new AllocationTokenCustomLogic());
    assertThat(
            flowUtils.checkDomainsWithToken(
                ImmutableList.of(
                    InternetDomainName.from("blah.tld"), InternetDomainName.from("blah2.tld")),
                "tokeN",
                "TheRegistrar",
                DateTime.now(UTC)))
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                InternetDomainName.from("blah.tld"), "", InternetDomainName.from("blah2.tld"), ""))
        .inOrder();
  }

  @Test
  public void test_checkDomainsWithToken_showsFailureMessageForRedeemedToken() {
    persistResource(
        new AllocationToken.Builder()
            .setToken("tokeN")
            .setTokenType(SINGLE_USE)
            .setRedemptionHistoryEntry(Key.create(HistoryEntry.class, 101L))
            .build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new AllocationTokenCustomLogic());
    assertThat(
            flowUtils.checkDomainsWithToken(
                ImmutableList.of(
                    InternetDomainName.from("blah.tld"), InternetDomainName.from("blah2.tld")),
                "tokeN",
                "TheRegistrar",
                DateTime.now(UTC)))
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                InternetDomainName.from("blah.tld"),
                "Alloc token was already redeemed",
                InternetDomainName.from("blah2.tld"),
                "Alloc token was already redeemed"))
        .inOrder();
  }

  @Test
  public void test_checkDomainsWithToken_callsCustomLogic() {
    persistResource(
        new AllocationToken.Builder().setToken("tokeN").setTokenType(SINGLE_USE).build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new FailingAllocationTokenCustomLogic());
    Exception thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                flowUtils.checkDomainsWithToken(
                    ImmutableList.of(
                        InternetDomainName.from("blah.tld"), InternetDomainName.from("blah2.tld")),
                    "tokeN",
                    "TheRegistrar",
                    DateTime.now(UTC)));
    assertThat(thrown).hasMessageThat().isEqualTo("failed for tests");
  }

  @Test
  public void test_checkDomainsWithToken_resultsFromCustomLogicAreIntegrated() {
    persistResource(
        new AllocationToken.Builder().setToken("tokeN").setTokenType(SINGLE_USE).build());
    AllocationTokenFlowUtils flowUtils =
        new AllocationTokenFlowUtils(new CustomResultAllocationTokenCustomLogic());
    assertThat(
            flowUtils.checkDomainsWithToken(
                ImmutableList.of(
                    InternetDomainName.from("blah.tld"), InternetDomainName.from("bunny.tld")),
                "tokeN",
                "TheRegistrar",
                DateTime.now(UTC)))
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                InternetDomainName.from("blah.tld"),
                "",
                InternetDomainName.from("bunny.tld"),
                "fufu"))
        .inOrder();
  }

  private static DomainCommand.Create createCommand(String domainName) {
    DomainCommand.Create command = mock(DomainCommand.Create.class);
    when(command.getFullyQualifiedDomainName()).thenReturn(domainName);
    return command;
  }

  /** An {@link AllocationTokenCustomLogic} class that throws exceptions on every method. */
  private static class FailingAllocationTokenCustomLogic extends AllocationTokenCustomLogic {

    @Override
    public AllocationToken verifyToken(
        DomainCommand.Create command,
        AllocationToken token,
        Registry registry,
        String clientId,
        DateTime now) {
      throw new IllegalStateException("failed for tests");
    }

    @Override
    public ImmutableMap<InternetDomainName, String> checkDomainsWithToken(
        ImmutableMap<InternetDomainName, String> checkResults,
        AllocationToken tokenEntity,
        String clientId,
        DateTime now) {
      throw new IllegalStateException("failed for tests");
    }
  }

  /** An {@link AllocationTokenCustomLogic} class that returns custom check results for bunnies. */
  private static class CustomResultAllocationTokenCustomLogic extends AllocationTokenCustomLogic {

    @Override
    public ImmutableMap<InternetDomainName, String> checkDomainsWithToken(
        ImmutableMap<InternetDomainName, String> checkResults,
        AllocationToken tokenEntity,
        String clientId,
        DateTime now) {
      return checkResults
          .entrySet()
          .stream()
          .collect(
              ImmutableMap.toImmutableMap(
                  Map.Entry::getKey,
                  entry ->
                      entry.getKey().toString().contains("bunny") ? "fufu" : entry.getValue()));
    }
  }
}
