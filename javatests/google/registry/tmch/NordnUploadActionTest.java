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

package google.registry.tmch;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.LOCATION;
import static com.google.common.net.MediaType.FORM_DATA;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistDomainAndEnqueueLordn;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.util.UrlFetchUtils.getHeaderFirst;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.common.base.Optional;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.UrlFetchException;
import java.net.URL;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link NordnUploadAction}. */
@RunWith(MockitoJUnitRunner.class)
public class NordnUploadActionTest {

  private static final String CLAIMS_CSV = "1,2000-01-01T00:00:00.000Z,1\n"
      + "roid,domain-name,notice-id,registrar-id,registration-datetime,ack-datetime,"
      + "application-datetime\n"
      + "2-TLD,claims-landrush1.tld,landrush1tcn,99999,2000-01-01T00:00:00.000Z,"
      + "1969-12-31T23:00:00.000Z,1969-12-31T00:00:00.000Z\n";

  private static final String SUNRISE_CSV = "1,2000-01-01T00:00:00.000Z,1\n"
      + "roid,domain-name,SMD-id,registrar-id,registration-datetime,application-datetime\n"
      + "2-TLD,sunrise1.tld,my-smdid,99999,2000-01-01T00:00:00.000Z,1969-12-31T00:00:00.000Z\n";

  private static final String LOCATION_URL = "http://trololol";

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Mock
  private URLFetchService fetchService;

  @Captor
  private ArgumentCaptor<HTTPRequest> httpRequestCaptor;

  @Mock
  private HTTPResponse httpResponse;

  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  private final LordnRequestInitializer lordnRequestInitializer = new LordnRequestInitializer();
  private final NordnUploadAction action = new NordnUploadAction();

  @Before
  public void before() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    when(fetchService.fetch(any(HTTPRequest.class))).thenReturn(httpResponse);
    when(httpResponse.getResponseCode()).thenReturn(SC_ACCEPTED);
    when(httpResponse.getHeadersUncombined())
        .thenReturn(ImmutableList.of(new HTTPHeader(LOCATION, "http://trololol")));
    persistResource(
        Registrar.loadByClientId("TheRegistrar").asBuilder().setIanaIdentifier(99999L).build());
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setLordnUsername("lolcat").build());
    lordnRequestInitializer.marksdbLordnPassword = Optional.of("attack");
    action.clock = clock;
    action.fetchService = fetchService;
    action.lordnRequestInitializer = lordnRequestInitializer;
    action.phase = "claims";
    action.tld = "tld";
    action.tmchMarksdbUrl = "http://127.0.0.1";
  }

  @Test
  public void testRun_claimsMode_appendsTldAndClaimsToRequestUrl() throws Exception {
    persistClaimsModeDomain();
    action.run();
    assertThat(getCapturedHttpRequest().getURL())
        .isEqualTo(new URL("http://127.0.0.1/LORDN/tld/claims"));
  }

  @Test
  public void testRun_sunriseMode_appendsTldAndClaimsToRequestUrl() throws Exception {
    persistSunriseModeDomain();
    action.run();
    assertThat(getCapturedHttpRequest().getURL())
        .isEqualTo(new URL("http://127.0.0.1/LORDN/tld/sunrise"));
  }

  @Test
  public void testRun_usesMultipartContentType() throws Exception {
    persistClaimsModeDomain();
    action.run();
    assertThat(getHeaderFirst(getCapturedHttpRequest(), CONTENT_TYPE).get())
        .startsWith("multipart/form-data; boundary=");
  }

  @Test
  public void testRun_hasPassword_setsAuthorizationHeader() throws Exception {
    persistClaimsModeDomain();
    action.run();
    assertThat(getHeaderFirst(getCapturedHttpRequest(), AUTHORIZATION))
        .hasValue("Basic bG9sY2F0OmF0dGFjaw==");  // echo -n lolcat:attack | base64
  }

  @Test
  public void testRun_noPassword_doesntSendAuthorizationHeader() throws Exception {
    lordnRequestInitializer.marksdbLordnPassword = Optional.absent();
    persistClaimsModeDomain();
    action.run();
    assertThat(getHeaderFirst(getCapturedHttpRequest(), AUTHORIZATION)).isAbsent();
  }

  @Test
  public void testRun_claimsMode_payloadMatchesClaimsCsv() throws Exception {
    persistClaimsModeDomain();
    action.run();
    assertThat(new String(getCapturedHttpRequest().getPayload(), UTF_8)).contains(CLAIMS_CSV);
  }

  @Test
  public void testRun_claimsMode_verifyTaskGetsEnqueuedWithClaimsCsv() throws Exception {
    persistClaimsModeDomain();
    action.run();
    assertTasksEnqueued(NordnVerifyAction.QUEUE, new TaskMatcher()
        .url(NordnVerifyAction.PATH)
        .header(NordnVerifyAction.URL_HEADER, LOCATION_URL)
        .header(CONTENT_TYPE, FORM_DATA.toString())
        .param(NordnVerifyAction.PARAM_CSV_DATA, CLAIMS_CSV));
  }

  @Test
  public void testRun_sunriseMode_payloadMatchesSunriseCsv() throws Exception {
    persistSunriseModeDomain();
    action.run();
    assertThat(new String(getCapturedHttpRequest().getPayload(), UTF_8)).contains(SUNRISE_CSV);
  }

  @Test
  public void testRun_sunriseMode_verifyTaskGetsEnqueuedWithSunriseCsv() throws Exception {
    persistSunriseModeDomain();
    action.run();
    assertTasksEnqueued(NordnVerifyAction.QUEUE, new TaskMatcher()
        .url(NordnVerifyAction.PATH)
        .header(NordnVerifyAction.URL_HEADER, LOCATION_URL)
        .header(CONTENT_TYPE, FORM_DATA.toString())
        .param(NordnVerifyAction.PARAM_CSV_DATA, SUNRISE_CSV));
  }

  @Test
  public void testFailure_nullRegistryUser() throws Exception {
    persistClaimsModeDomain();
    persistResource(Registry.get("tld").asBuilder().setLordnUsername(null).build());
    thrown.expect(VerifyException.class, "lordnUsername is not set for tld.");
    action.run();
  }

  @Test
  public void testFetchFailure() throws Exception {
    persistClaimsModeDomain();
    when(httpResponse.getResponseCode()).thenReturn(SC_INTERNAL_SERVER_ERROR);
    thrown.expect(UrlFetchException.class);
    action.run();
  }

  private HTTPRequest getCapturedHttpRequest() throws Exception {
    verify(fetchService).fetch(httpRequestCaptor.capture());
    return httpRequestCaptor.getAllValues().get(0);
  }

  private void persistClaimsModeDomain() {
    DomainResource domain = newDomainResource("claims-landrush1.tld");
    persistDomainAndEnqueueLordn(domain.asBuilder()
        .setLaunchNotice(LaunchNotice.create(
            "landrush1tcn", null, null, domain.getCreationTime().minusHours(1)))
        .setApplicationTime(domain.getCreationTime().minusDays(1))
        .build());
  }

  private void persistSunriseModeDomain() {
    action.phase = "sunrise";
    DomainResource domain = newDomainResource("sunrise1.tld");
    persistDomainAndEnqueueLordn(domain.asBuilder()
        .setSmdId("my-smdid")
        .setApplicationTime(domain.getCreationTime().minusDays(1))
        .build());
  }
}
