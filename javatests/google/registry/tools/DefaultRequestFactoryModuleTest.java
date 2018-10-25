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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import google.registry.config.RegistryConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultRequestFactoryModuleTest {

  private static final Credential FAKE_CREDENTIAL = new Credential(
      new Credential.AccessMethod() {
        @Override
        public void intercept(HttpRequest request, String accessToken) {}

        @Override
        public String getAccessTokenFromRequest(HttpRequest request) {
          return "MockAccessToken";
        }
      });

  DefaultRequestFactoryModule module = new DefaultRequestFactoryModule();

  @Before
  public void setUp() {
    RegistryToolEnvironment.UNITTEST.setup();
  }

  @Test
  public void test_provideHttpRequestFactory_localhost() {
    // Make sure that localhost creates a request factory with an initializer.
    RegistryConfig.CONFIG_SETTINGS.get().appEngine.isLocal = true;
    HttpRequestFactory factory = module.provideHttpRequestFactory(() -> FAKE_CREDENTIAL);
    HttpRequestInitializer initializer = factory.getInitializer();
    assertThat(initializer).isNotNull();
    assertThat(initializer).isNotSameAs(FAKE_CREDENTIAL);
  }

  @Test
  public void test_provideHttpRequestFactory_remote() {
    // Make sure that example.com creates a request factory with the UNITTEST client id but no
    RegistryConfig.CONFIG_SETTINGS.get().appEngine.isLocal = false;
    HttpRequestFactory factory = module.provideHttpRequestFactory(() -> FAKE_CREDENTIAL);
    assertThat(factory.getInitializer()).isSameAs(FAKE_CREDENTIAL);
  }
}
