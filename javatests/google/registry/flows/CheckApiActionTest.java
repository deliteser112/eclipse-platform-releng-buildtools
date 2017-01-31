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

package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableSet;
import google.registry.flows.EppTestComponent.FakesAndMocksModule;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeResponse;
import java.util.Map;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckApiAction}. */
@RunWith(JUnit4.class)
public class CheckApiActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  final CheckApiAction action = new CheckApiAction();

  @Before
  public void init() throws Exception {
    createTld("example");
    persistResource(
        Registry.get("example")
            .asBuilder()
            .setReservedLists(persistReservedList("example-reserved", "foo,FULLY_BLOCKED"))
            .build());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getCheckResponse(String domain) {
    action.domain = domain;
    action.response = new FakeResponse();
    action.checkApiServletRegistrarClientId = "TheRegistrar";
    action.eppController = DaggerEppTestComponent.builder()
        .fakesAndMocksModule(new FakesAndMocksModule())
        .build()
        .startRequest()
        .eppController();
    action.run();
    return (Map<String, Object>) JSONValue.parse(((FakeResponse) action.response).getPayload());
  }

  @Test
  public void testFailure_nullDomain() throws Exception {
    assertThat(getCheckResponse(null)).containsExactly(
        "status", "error",
        "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_emptyDomain() throws Exception {
    assertThat(getCheckResponse("")).containsExactly(
        "status", "error",
        "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_invalidDomain() throws Exception {
    assertThat(getCheckResponse("@#$%^")).containsExactly(
        "status", "error",
        "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_singlePartDomain() throws Exception {
    assertThat(getCheckResponse("foo")).containsExactly(
        "status", "error",
        "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_nonExistentTld() throws Exception {
    assertThat(getCheckResponse("foo.bar")).containsExactly(
        "status", "error",
        "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_unauthorizedTld() throws Exception {
    createTld("foo");
    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("foo"))
            .build());
    assertThat(getCheckResponse("timmy.example")).containsExactly(
        "status", "error",
        "reason", "Registrar is not authorized to access the TLD example");
  }

  @Test
  public void testSuccess_availableStandard() throws Exception {
    assertThat(getCheckResponse("somedomain.example")).containsExactly(
        "status", "success",
        "available", true,
        "tier", "standard");
  }

  @Test
  public void testSuccess_availableCapital() throws Exception {
    assertThat(getCheckResponse("SOMEDOMAIN.EXAMPLE")).containsExactly(
        "status", "success",
        "available", true,
        "tier", "standard");
  }

  @Test
  public void testSuccess_availableUnicode() throws Exception {
    assertThat(getCheckResponse("ééé.example")).containsExactly(
        "status", "success",
        "available", true,
        "tier", "standard");
  }

  @Test
  public void testSuccess_availablePunycode() throws Exception {
    assertThat(getCheckResponse("xn--9caaa.example")).containsExactly(
        "status", "success",
        "available", true,
        "tier", "standard");
  }

  @Test
  public void testSuccess_availablePremium() throws Exception {
    assertThat(getCheckResponse("rich.example")).containsExactly(
        "status", "success",
        "available", true,
        "tier", "premium");
  }

  @Test
  public void testSuccess_alreadyRegistered() throws Exception {
    persistActiveDomain("somedomain.example");
    assertThat(getCheckResponse("somedomain.example")).containsExactly(
        "status", "success",
        "available", false,
        "reason", "In use");
  }

  @Test
  public void testSuccess_reserved() throws Exception {
    assertThat(getCheckResponse("foo.example")).containsExactly(
        "status", "success",
        "available", false,
        "reason", "Reserved");
  }
}
