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

package com.google.domain.registry.ui.server.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistReservedList;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.testing.AppEngineRule;

import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Tests for {@link CheckApiServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class CheckApiServletTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Mock HttpServletRequest req;
  @Mock HttpServletResponse rsp;

  private final StringWriter writer = new StringWriter();

  private final CheckApiServlet servlet = new CheckApiServlet();

  @Before
  public void init() throws Exception {
    createTld("example");
    persistResource(
        Registry.get("example")
            .asBuilder()
            .setReservedLists(persistReservedList("example-reserved", "foo,FULLY_BLOCKED"))
            .build());
    when(rsp.getWriter()).thenReturn(new PrintWriter(writer));
  }

  private void doTest(Map<String, ?> expected) throws Exception {
    servlet.doGet(req, rsp);
    assertThat(JSONValue.parse(writer.toString())).isEqualTo(expected);
  }

  @Test
  public void testFailure_nullDomain() throws Exception {
    doTest(ImmutableMap.of(
        "status", "error",
        "reason", "Must supply a valid second level domain name"));
  }

  @Test
  public void testFailure_emptyDomain() throws Exception {
    when(req.getParameter("domain")).thenReturn("");
    doTest(ImmutableMap.of(
        "status", "error",
        "reason", "Must supply a valid second level domain name"));
  }

  @Test
  public void testFailure_invalidDomain() throws Exception {
    when(req.getParameter("domain")).thenReturn("@#$%^");
    doTest(ImmutableMap.of(
        "status", "error",
        "reason", "Must supply a valid second level domain name"));
  }

  @Test
  public void testFailure_singlePartDomain() throws Exception {
    when(req.getParameter("domain")).thenReturn("foo");
    doTest(ImmutableMap.of(
        "status", "error",
        "reason", "Must supply a valid second level domain name"));
  }

  @Test
  public void testFailure_nonExistentTld() throws Exception {
    when(req.getParameter("domain")).thenReturn("foo.bar");
    doTest(ImmutableMap.of(
        "status", "error",
        "reason", "Domain name is under tld bar which doesn't exist"));
  }

  @Test
  public void testFailure_unauthorizedTld() throws Exception {
    createTld("foo");
    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("foo"))
            .build());
    when(req.getParameter("domain")).thenReturn("timmy.example");
    doTest(ImmutableMap.of(
        "status", "error",
        "reason", "Registrar is not authorized to access the TLD example"));
  }

  @Test
  public void testSuccess_availableStandard() throws Exception {
    when(req.getParameter("domain")).thenReturn("somedomain.example");
    doTest(ImmutableMap.of(
        "status", "success",
        "available", true,
        "tier", "standard"));
  }

  @Test
  public void testSuccess_availableCapital() throws Exception {
    when(req.getParameter("domain")).thenReturn("SOMEDOMAIN.EXAMPLE");
    doTest(ImmutableMap.of(
        "status", "success",
        "available", true,
        "tier", "standard"));
  }

  @Test
  public void testSuccess_availableUnicode() throws Exception {
    when(req.getParameter("domain")).thenReturn("ééé.example");
    doTest(ImmutableMap.of(
        "status", "success",
        "available", true,
        "tier", "standard"));
  }

  @Test
  public void testSuccess_availablePunycode() throws Exception {
    when(req.getParameter("domain")).thenReturn("xn--9caaa.example");
    doTest(ImmutableMap.of(
        "status", "success",
        "available", true,
        "tier", "standard"));
  }

  @Test
  public void testSuccess_availablePremium() throws Exception {
    when(req.getParameter("domain")).thenReturn("rich.example");
    doTest(ImmutableMap.of(
        "status", "success",
        "available", true,
        "tier", "premium"));
  }

  @Test
  public void testSuccess_alreadyRegistered() throws Exception {
    persistActiveDomain("somedomain.example");
    when(req.getParameter("domain")).thenReturn("somedomain.example");
    doTest(ImmutableMap.of(
        "status", "success",
        "available", false,
        "reason", "In use"));
  }

  @Test
  public void testSuccess_reserved() throws Exception {
    when(req.getParameter("domain")).thenReturn("foo.example");
    doTest(ImmutableMap.of(
        "status", "success",
        "available", false,
        "reason", "Reserved"));
  }
}
