// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import java.util.Map;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckApi2Action}. */
@RunWith(JUnit4.class)
public class CheckApi2ActionTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

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
    CheckApi2Action action = new CheckApi2Action();
    action.domain = domain;
    action.response = new FakeResponse();
    action.clock = new FakeClock();
    action.run();
    return (Map<String, Object>) JSONValue.parse(((FakeResponse) action.response).getPayload());
  }

  @Test
  public void testFailure_nullDomain() throws Exception {
    assertThat(getCheckResponse(null))
        .containsExactly(
            "status", "error",
            "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_emptyDomain() throws Exception {
    assertThat(getCheckResponse(""))
        .containsExactly(
            "status", "error",
            "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_invalidDomain() throws Exception {
    assertThat(getCheckResponse("@#$%^"))
        .containsExactly(
            "status", "error",
            "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_singlePartDomain() throws Exception {
    assertThat(getCheckResponse("foo"))
        .containsExactly(
            "status", "error",
            "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_nonExistentTld() throws Exception {
    assertThat(getCheckResponse("foo.bar"))
        .containsExactly(
            "status", "error",
            "reason", "Must supply a valid domain name on an authoritative TLD");
  }

  @Test
  public void testFailure_invalidIdnTable() throws Exception {
    assertThat(getCheckResponse("ΑΒΓ.example"))
        .containsExactly(
            "status", "error",
            "reason", "Domain label is not allowed by IDN table");
  }

  @Test
  public void testFailure_tldInPredelegation() throws Exception {
    createTld("predelegated", TldState.PREDELEGATION);
    assertThat(getCheckResponse("foo.predelegated"))
        .containsExactly(
            "status", "error",
            "reason", "Check in this TLD is not allowed in the current registry phase");
  }

  @Test
  public void testSuccess_availableStandard() throws Exception {
    assertThat(getCheckResponse("somedomain.example"))
        .containsExactly(
            "status", "success",
            "available", true,
            "tier", "standard");
  }

  @Test
  public void testSuccess_availableCapital() throws Exception {
    assertThat(getCheckResponse("SOMEDOMAIN.EXAMPLE"))
        .containsExactly(
            "status", "success",
            "available", true,
            "tier", "standard");
  }

  @Test
  public void testSuccess_availableUnicode() throws Exception {
    assertThat(getCheckResponse("ééé.example"))
        .containsExactly(
            "status", "success",
            "available", true,
            "tier", "standard");
  }

  @Test
  public void testSuccess_availablePunycode() throws Exception {
    assertThat(getCheckResponse("xn--9caaa.example"))
        .containsExactly(
            "status", "success",
            "available", true,
            "tier", "standard");
  }

  @Test
  public void testSuccess_availablePremium() throws Exception {
    assertThat(getCheckResponse("rich.example"))
        .containsExactly(
            "status", "success",
            "available", true,
            "tier", "premium");
  }

  @Test
  public void testSuccess_alreadyRegistered() throws Exception {
    persistActiveDomain("somedomain.example");
    assertThat(getCheckResponse("somedomain.example"))
        .containsExactly(
            "status", "success",
            "available", false,
            "reason", "In use");
  }

  @Test
  public void testSuccess_reserved() throws Exception {
    assertThat(getCheckResponse("foo.example"))
        .containsExactly(
            "status", "success",
            "available", false,
            "reason", "Reserved");
  }
}
