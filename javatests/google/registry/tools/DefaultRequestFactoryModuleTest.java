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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.common.net.HostAndPort;
import google.registry.testing.Providers;
import java.io.IOException;
import javax.inject.Provider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

@RunWith(JUnit4.class)
public class DefaultRequestFactoryModuleTest {

  private static final Credential FAKE_CREDENTIAL = new Credential(
      new Credential.AccessMethod() {
        @Override
        public void intercept(HttpRequest request, String accessToken) throws IOException {}

        @Override
        public String getAccessTokenFromRequest(HttpRequest request) {
          return "MockAccessToken";
        }
      });

  private static final String TEST_CLIENT_SECRET_FILENAME =
      "/google/registry/tools/resources/client_secret_UNITTEST.json";

  // Mocks.
  AbstractDataStoreFactory dataStoreFactory = mock(AbstractDataStoreFactory.class);
  DefaultRequestFactoryModule.Authorizer authorizer =
      mock(DefaultRequestFactoryModule.Authorizer.class);
  Provider<Credential> credentialProvider = Providers.of(FAKE_CREDENTIAL);

  // Captor for client secrets.
  ArgumentCaptor<GoogleClientSecrets> secrets = ArgumentCaptor.forClass(GoogleClientSecrets.class);

  DefaultRequestFactoryModule module = new DefaultRequestFactoryModule();

  @Before
  public void setUp() {
    RegistryToolEnvironment.UNITTEST.setup();
  }

  @Test
  public void test_getCredential() throws Exception {
    when(authorizer.authorize(any(GoogleClientSecrets.class))).thenReturn(FAKE_CREDENTIAL);
    Credential cred = module.provideCredential(
        dataStoreFactory,
        authorizer,
        TEST_CLIENT_SECRET_FILENAME);
    assertThat(cred).isSameAs(FAKE_CREDENTIAL);
    verify(authorizer).authorize(secrets.capture());
    assertThat(secrets.getValue().getDetails().getClientId()).isEqualTo(
        "UNITTEST-CLIENT-ID");
  }

  @Test
  public void test_provideHttpRequestFactory_localhost() throws Exception {
    // Make sure that localhost creates a request factory with an initializer.
    HttpRequestFactory factory =
        module.provideHttpRequestFactory(new AppEngineConnectionFlags(
                HostAndPort.fromParts("localhost", 1000)),
            credentialProvider);
    HttpRequestInitializer initializer = factory.getInitializer();
    assertThat(initializer).isNotNull();
    assertThat(initializer).isNotSameAs(FAKE_CREDENTIAL);
    verifyZeroInteractions(authorizer);
  }

  @Test
  public void test_provideHttpRequestFactory_remote() throws Exception {
    // Make sure that example.com creates a request factory with the UNITTEST client id but no
    // initializer.
    HttpRequestFactory factory =
        module.provideHttpRequestFactory(new AppEngineConnectionFlags(
                HostAndPort.fromParts("example.com", 1000)),
            credentialProvider);
    assertThat(factory.getInitializer()).isSameAs(FAKE_CREDENTIAL);
  }
}
