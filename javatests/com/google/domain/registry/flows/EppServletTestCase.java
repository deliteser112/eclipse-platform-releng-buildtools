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

package com.google.domain.registry.flows;

import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.security.XsrfTokenManager.X_CSRF_TOKEN;
import static com.google.domain.registry.security.XsrfTokenManager.generateToken;
import static com.google.domain.registry.testing.DatastoreHelper.createTlds;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static com.google.domain.registry.xml.XmlTestUtils.assertXmlEqualsWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.DateTimeZone.UTC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.modules.ModulesService;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.tmch.ClaimsListShard.ClaimsListSingleton;
import com.google.domain.registry.monitoring.whitebox.Metrics;
import com.google.domain.registry.security.XsrfProtectedServlet;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.FakeServletInputStream;
import com.google.domain.registry.testing.InjectRule;
import com.google.domain.registry.util.BasicHttpSession;
import com.google.domain.registry.util.TypeUtils.TypeInstantiator;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Test setup for all EppServletTest subclasses.
 *
 * @param <S> The EppXXXServlet class to test.
 */
public abstract class EppServletTestCase<S extends HttpServlet> {

  @Rule
  public final InjectRule inject = new InjectRule();

  @Mock
  HttpServletRequest req;

  @Mock
  HttpServletResponse rsp;

  @Mock
  ModulesService modulesService;

  HttpSession session;

  FakeClock clock = new FakeClock();

  private String currentTld = null;
  private Optional<Boolean> isSuperuser = Optional.<Boolean> absent();
  private Optional<String> clientIdentifier = Optional.<String> absent();

  void setSuperuser(boolean isSuperuser) {
    this.isSuperuser = Optional.of(isSuperuser);
  }

  void setClientIdentifier(String clientIdentifier) {
    this.clientIdentifier = Optional.of(clientIdentifier);
  }

  static final DateTime START_OF_GA = DateTime.parse("2014-03-01T00:00:00Z");

  @Before
  public final void init() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);  // For transactional flows.
    inject.setStaticField(FlowRunner.class, "clock", clock);  // For non-transactional flows.
    inject.setStaticField(Metrics.class, "modulesService", modulesService);
    when(modulesService.getVersionHostname("backend", null)).thenReturn("backend.hostname");

    // Create RegistryData for all TLDs used in these tests.
    // We want to create all of these even for tests that don't use them to make sure that
    // tld-selection works correctly.
    createTlds("net", "xn--q9jyb4c", "example");
    ofy().saveWithoutBackup().entity(new ClaimsListSingleton()).now();

    session = new BasicHttpSession();
    persistResource(
        Registrar.loadByClientId("NewRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("net", "example", "xn--q9jyb4c"))
            .build());

    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("net", "example", "xn--q9jyb4c"))
            .build());
  }

  void assertCommandAndResponse(
      String inputFilename,
      Map<String, String> inputSubstitutions,
      String outputFilename,
      Map<String, String> outputSubstitutions) throws Exception {
    assertCommandAndResponse(
        inputFilename,
        inputSubstitutions,
        outputFilename,
        outputSubstitutions,
        DateTime.now(UTC));
  }

  String assertCommandAndResponse(String inputFilename, String outputFilename) throws Exception {
    return assertCommandAndResponse(inputFilename, outputFilename, DateTime.now(UTC));
  }

  String assertCommandAndResponse(
      String inputFilename,
      Map<String, String> inputSubstitutions,
      String outputFilename,
      Map<String, String> outputSubstitutions,
      String nowString) throws Exception {
    return assertCommandAndResponse(
        inputFilename,
        inputSubstitutions,
        outputFilename,
        outputSubstitutions,
        DateTime.parse(nowString));
  }

  String assertCommandAndResponse(String inputFilename, String outputFilename, String nowString)
      throws Exception {
    return assertCommandAndResponse(inputFilename, outputFilename, DateTime.parse(nowString));
  }

  String assertCommandAndResponse(String inputFilename, String outputFilename, DateTime now)
      throws Exception {
    return assertCommandAndResponse(inputFilename, null, outputFilename, null, now);
  }

  String assertCommandAndResponse(
      String inputFilename,
      Map<String, String> inputSubstitutions,
      String outputFilename,
      Map<String, String> outputSubstitutions,
      DateTime now) throws Exception {
    String outputFile =
        loadFileWithSubstitutions(EppServletTestCase.class, outputFilename, outputSubstitutions);
    String actualOutput = expectXmlCommand(loadFileWithSubstitutions(
        EppServletTestCase.class, inputFilename, inputSubstitutions), now);
    assertXmlEqualsWithMessage(
        outputFile,
        actualOutput,
        "Running " + inputFilename + " => " + outputFilename,
        "epp.response.resData.infData.roid",
        "epp.response.trID.svTRID");
    ofy().clearSessionCache();  // Clear the cache like OfyFilter would.
    return actualOutput;
  }

  HttpSession getOrRenewSession() {
    // Try an idempotent op on the session to see if it's valid.
    try {
      session.getAttribute(null);
      return session;
    } catch (IllegalStateException e) {
      // Session is invalid.
      session = new BasicHttpSession();
      return session;
    }
  }

  @SuppressWarnings("resource")
  String expectXmlCommand(String inputFile, DateTime now) throws Exception {
    clock.setTo(now);  // Makes Ofy use 'now' as its time
    reset(req, rsp);
    HttpServlet servlet = new TypeInstantiator<S>(getClass()){}.instantiate();
    if (servlet instanceof XsrfProtectedServlet) {
      when(req.getHeader(X_CSRF_TOKEN))
          .thenReturn(generateToken(((XsrfProtectedServlet) servlet).getScope()));
    }
    when(req.getInputStream()).thenReturn(new FakeServletInputStream(inputFile.getBytes(UTF_8)));
    when(req.getParameter("xml")).thenReturn(inputFile);
    if (isSuperuser.isPresent()) {
      when(req.getParameter("superuser")).thenReturn(isSuperuser.get().toString());
    }
    if (clientIdentifier.isPresent()) {
      when(req.getParameter("clientIdentifier")).thenReturn(clientIdentifier.get());
    }
    when(req.getParameter("tld")).thenReturn(currentTld);
    when(req.getServletPath()).thenReturn("");
    when(req.getMethod()).thenReturn("POST");
    when(req.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
    when(req.getSession(true)).thenAnswer(new Answer<HttpSession>() {
        @Override
        public HttpSession answer(InvocationOnMock invocation) {
          return getOrRenewSession();
        }});
    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    when(rsp.getOutputStream()).thenReturn(new ServletOutputStream() {
        @Override
        public void write(int b) {
          byteArrayOutputStream.write(b);
        }});
    extendedSessionConfig(inputFile);

    servlet.init(mock(ServletConfig.class));
    servlet.service(req, rsp);
    verify(rsp).setStatus(HttpServletResponse.SC_OK);
    String result = new String(byteArrayOutputStream.toByteArray(), UTF_8);
    // Run the resulting xml through the unmarshaller to verify that it was valid.
    EppXmlTransformer.validateOutput(result);
    return result;
  }

  /** Create the two administrative contacts and two hosts that are used by a lot of our tests. */
  protected void createContactsAndHosts() throws Exception {
    DateTime startTime = DateTime.parse("2000-06-01T00:00:00Z");
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        startTime);
    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        "contact_create_response_jd1234.xml",
        startTime.plusMinutes(1));
    assertCommandAndResponse(
        "host_create.xml",
        "host_create_response.xml",
        startTime.plusMinutes(2));
    assertCommandAndResponse(
        "host_create2.xml",
        "host_create2_response.xml",
        startTime.plusMinutes(3));
  }

  /**
   * Creates the domain fakesite.example with two nameservers on it.
   */
  protected void createFakesite() throws Exception {
    createContactsAndHosts();
    assertCommandAndResponse(
        "domain_create_fakesite.xml",
        "domain_create_response_fakesite.xml",
        "2000-06-01T00:04:00Z");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_ok.xml",
        "2000-06-06T00:00:00Z");
  }

  // Adds ns3.fakesite.example as a host, then adds it to fakesite.
  protected void createSubordinateHost() throws Exception {
    // Add the fakesite nameserver (requires that domain is already created).
    assertCommandAndResponse(
        "host_create_fakesite.xml",
        "host_create_response_fakesite.xml",
        "2000-06-06T00:01:00Z");
    // Add new nameserver to domain.
    assertCommandAndResponse(
        "domain_update_add_nameserver_fakesite.xml",
        "domain_update_add_nameserver_response_fakesite.xml",
        "2000-06-08T00:00:00Z");
    // Verify new nameserver was added.
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_3_nameservers.xml",
        "2000-06-08T00:01:00Z");
    // Verify that nameserver's data was set correctly.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        "host_info_response_fakesite.xml",
        "2000-06-08T00:02:00Z");
  }

  /** For subclasses to further setup the session. */
  protected void extendedSessionConfig(
      @SuppressWarnings("unused") String inputFile) throws Exception {}
}
