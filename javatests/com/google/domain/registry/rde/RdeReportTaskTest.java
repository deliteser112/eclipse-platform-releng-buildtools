// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.rde;

import static com.google.appengine.api.urlfetch.HTTPMethod.PUT;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.GcsTestingUtils.writeGcsFile;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.joda.time.Duration.standardDays;
import static org.joda.time.Duration.standardSeconds;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.domain.registry.config.RegistryConfig;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.gcs.GcsUtils;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.RegistryCursor.CursorType;
import com.google.domain.registry.request.HttpException.InternalServerErrorException;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.BouncyCastleProviderRule;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.xjc.XjcXmlTransformer;
import com.google.domain.registry.xjc.rdereport.XjcRdeReportReport;
import com.google.domain.registry.xml.XmlException;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.Map;

/** Unit tests for {@link RdeReportTask}. */
@RunWith(JUnit4.class)
public class RdeReportTaskTest {

  private static final ByteSource REPORT_XML = RdeTestData.get("report.xml");
  private static final ByteSource IIRDEA_BAD_XML = RdeTestData.get("iirdea_bad.xml");
  private static final ByteSource IIRDEA_GOOD_XML = RdeTestData.get("iirdea_good.xml");

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final BouncyCastleProviderRule bouncy = new BouncyCastleProviderRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  private final FakeResponse response = new FakeResponse();
  private final EscrowTaskRunner runner = mock(EscrowTaskRunner.class);
  private final URLFetchService urlFetchService = mock(URLFetchService.class);
  private final ArgumentCaptor<HTTPRequest> request = ArgumentCaptor.forClass(HTTPRequest.class);
  private final HTTPResponse httpResponse = mock(HTTPResponse.class);

  private final GcsService gcsService = GcsServiceFactory.createGcsService();
  private final RegistryConfig config = RegistryEnvironment.get().config();
  private final GcsFilename reportFile =
      new GcsFilename("tub", "test_2006-06-06_full_S1_R0-report.xml.ghostryde");

  private RdeReportTask createTask() {
    RdeReporter reporter = new RdeReporter();
    reporter.config = config;
    reporter.reportUrlPrefix = "https://rde-report.example";
    reporter.urlFetchService = urlFetchService;
    reporter.password = "foo";
    RdeReportTask task = new RdeReportTask();
    task.gcsUtils = new GcsUtils(gcsService, 1024);
    task.ghostryde = new Ghostryde(1024);
    task.response = response;
    task.bucket = "tub";
    task.tld = "test";
    task.interval = standardDays(1);
    task.reporter = reporter;
    task.timeout = standardSeconds(30);
    task.stagingDecryptionKey = new RdeKeyringModule().get().getRdeStagingDecryptionKey();
    task.runner = runner;
    return task;
  }

  @Before
  public void before() throws Exception {
    PGPPublicKey encryptKey = new RdeKeyringModule().get().getRdeStagingEncryptionKey();
    createTld("test");
    persistResource(RegistryCursor.create(
        Registry.get("test"), CursorType.RDE_REPORT, DateTime.parse("2006-06-06TZ")));
    persistResource(RegistryCursor.create(
        Registry.get("test"), CursorType.RDE_UPLOAD, DateTime.parse("2006-06-07TZ")));
    writeGcsFile(gcsService, reportFile,
        Ghostryde.encode(REPORT_XML.read(), encryptKey, "darkside.xml", DateTime.now()));
  }

  @Test
  public void testRun() throws Exception {
    createTld("lol");
    RdeReportTask task = createTask();
    task.tld = "lol";
    task.run();
    verify(runner).lockRunAndRollForward(
        task, Registry.get("lol"), standardSeconds(30), CursorType.RDE_REPORT, standardDays(1));
    verifyNoMoreInteractions(runner);
  }

  @Test
  public void testRunWithLock() throws Exception {
    when(httpResponse.getResponseCode()).thenReturn(SC_OK);
    when(httpResponse.getContent()).thenReturn(IIRDEA_GOOD_XML.read());
    when(urlFetchService.fetch(request.capture())).thenReturn(httpResponse);
    createTask().runWithLock(loadRdeReportCursor());
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("OK test 2006-06-06T00:00:00.000Z\n");

    // Verify the HTTP request was correct.
    assertThat(request.getValue().getMethod()).isSameAs(PUT);
    assertThat(request.getValue().getURL().getProtocol()).isEqualTo("https");
    assertThat(request.getValue().getURL().getPath()).endsWith("/test/20101017001");
    Map<String, String> headers = mapifyHeaders(request.getValue().getHeaders());
    assertThat(headers).containsEntry("CONTENT_TYPE", "text/xml");
    assertThat(headers)
        .containsEntry("AUTHORIZATION", "Basic dGVzdF9yeTpmb28=");

    // Verify the payload XML was the same as what's in testdata/report.xml.
    XjcRdeReportReport report = parseReport(request.getValue().getPayload());
    assertThat(report.getId()).isEqualTo("20101017001");
    assertThat(report.getCrDate()).isEqualTo(DateTime.parse("2010-10-17T00:15:00.0Z"));
    assertThat(report.getWatermark()).isEqualTo(DateTime.parse("2010-10-17T00:00:00Z"));
  }

  @Test
  public void testRunWithLock_badRequest_throws500WithErrorInfo() throws Exception {
    when(httpResponse.getResponseCode()).thenReturn(SC_BAD_REQUEST);
    when(httpResponse.getContent()).thenReturn(IIRDEA_BAD_XML.read());
    when(urlFetchService.fetch(request.capture())).thenReturn(httpResponse);
    thrown.expect(InternalServerErrorException.class, "The structure of the report is invalid.");
    createTask().runWithLock(loadRdeReportCursor());
  }

  @Test
  public void testRunWithLock_fetchFailed_throwsRuntimeException() throws Exception {
    class ExpectedException extends RuntimeException {}
    when(urlFetchService.fetch(any(HTTPRequest.class))).thenThrow(new ExpectedException());
    thrown.expect(ExpectedException.class);
    createTask().runWithLock(loadRdeReportCursor());
  }

  private DateTime loadRdeReportCursor() {
    return RegistryCursor.load(Registry.get("test"), CursorType.RDE_REPORT).get();
  }

  private static ImmutableMap<String, String> mapifyHeaders(Iterable<HTTPHeader> headers) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    for (HTTPHeader header : headers) {
      builder.put(header.getName().replace('-', '_').toUpperCase(), header.getValue());
    }
    return builder.build();
  }

  private static XjcRdeReportReport parseReport(byte[] data) {
    try {
      return XjcXmlTransformer.unmarshal(new ByteArrayInputStream(data));
    } catch (XmlException e) {
      throw new RuntimeException(e);
    }
  }
}
