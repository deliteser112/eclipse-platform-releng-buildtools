// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import google.registry.testing.AppEngineRule;
import google.registry.testing.BouncyCastleProviderRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Common code for unit tests of classes that extend {@link Marksdb}. */
@RunWith(MockitoJUnitRunner.class)
public class TmchActionTestCase {

  static final String MARKSDB_LOGIN = "lolcat:attack";
  static final String MARKSDB_LOGIN_BASE64 = "bG9sY2F0OmF0dGFjaw==";
  static final String MARKSDB_URL = "http://127.0.0.1/love";

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final BouncyCastleProviderRule bouncy = new BouncyCastleProviderRule();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Mock
  URLFetchService fetchService;

  @Captor
  ArgumentCaptor<HTTPRequest> httpRequest;

  @Mock
  HTTPResponse httpResponse;

  final FakeClock clock = new FakeClock();
  final Marksdb marksdb = new Marksdb();

  @Before
  public void commonBefore() throws Exception {
    inject.setStaticField(TmchCertificateAuthority.class, "clock", clock);
    marksdb.fetchService = fetchService;
    marksdb.tmchMarksdbUrl = MARKSDB_URL;
    marksdb.marksdbPublicKey = TmchData.loadPublicKey(TmchTestData.loadBytes("pubkey"));
    when(fetchService.fetch(any(HTTPRequest.class))).thenReturn(httpResponse);
    when(httpResponse.getResponseCode()).thenReturn(SC_OK);
  }
}
