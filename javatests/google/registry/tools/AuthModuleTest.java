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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.Serializable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthModuleTest {
  private static final String TEST_CLIENT_SECRET_FILENAME =
      "/google/registry/tools/resources/client_secret_UNITTEST.json";

  private static final Credential FAKE_CREDENTIAL = new Credential(
      new Credential.AccessMethod() {
        @Override
        public void intercept(HttpRequest request, String accessToken) throws IOException {}

        @Override
        public String getAccessTokenFromRequest(HttpRequest request) {
          return "MockAccessToken";
        }
      });

  @SuppressWarnings("unchecked")
  DataStore<StoredCredential> dataStore = mock(DataStore.class);

  class FakeDataStoreFactory extends AbstractDataStoreFactory {
    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) {
      @SuppressWarnings("unchecked")
      DataStore<V> result = (DataStore<V>) dataStore;
      return result;
    }
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void test_clientScopeQualifier() {
    AuthModule authModule = new AuthModule();
    String simpleQualifier =
        authModule.provideClientScopeQualifier("client-id", ImmutableSet.of("foo", "bar"));

    // If we change the way we encode client id and scopes, this assertion will break.  That's
    // probably ok and you can just change the text.  The things you have to be aware of are:
    // - Names in the new encoding should have a low risk of collision with the old encoding.
    // - Changing the encoding will force all OAuth users of the nomulus tool to do a new login
    //   (existing credentials will not be used).
    assertThat(simpleQualifier).isEqualTo("client-id bar foo");

    // Verify order independence.
    assertThat(simpleQualifier).isEqualTo(
        authModule.provideClientScopeQualifier("client-id", ImmutableSet.of("bar", "foo")));

    // Verify changing client id produces a different value.
    assertThat(simpleQualifier).isNotEqualTo(
        authModule.provideClientScopeQualifier("new-client", ImmutableSet.of("bar", "foo")));

    // Verify that adding/deleting/modifying scopes produces a different value.
    assertThat(simpleQualifier).isNotEqualTo(
        authModule.provideClientScopeQualifier("client id", ImmutableSet.of("bar", "foo", "baz")));
    assertThat(simpleQualifier).isNotEqualTo(
        authModule.provideClientScopeQualifier("client id", ImmutableSet.of("barx", "foo")));
    assertThat(simpleQualifier).isNotEqualTo(
        authModule.provideClientScopeQualifier("client id", ImmutableSet.of("bar", "foox")));
    assertThat(simpleQualifier).isNotEqualTo(
        authModule.provideClientScopeQualifier("client id", ImmutableSet.of("bar")));

    // Verify that delimiting works.
    assertThat(simpleQualifier).isNotEqualTo(
        authModule.provideClientScopeQualifier("client-id", ImmutableSet.of("barf", "oo")));
    assertThat(simpleQualifier).isNotEqualTo(
        authModule.provideClientScopeQualifier("client-idb", ImmutableSet.of("ar", "foo")));
  }

  private Credential getCredential() {
    // Reconstruct the entire dependency graph, injecting FakeDatastoreFactory and credential
    // parameters.
    AuthModule authModule = new AuthModule();
    JacksonFactory jsonFactory = new JacksonFactory();
    GoogleClientSecrets clientSecrets =
        authModule.provideClientSecrets(TEST_CLIENT_SECRET_FILENAME, jsonFactory);
    ImmutableSet<String> scopes = ImmutableSet.of("scope1");
    return authModule.provideCredential(
        authModule.provideAuthorizationCodeFlow(
            jsonFactory, clientSecrets, scopes, new FakeDataStoreFactory()),
        authModule.provideClientScopeQualifier(authModule.provideClientId(clientSecrets), scopes));
  }

  @Test
  public void test_provideCredential() throws Exception {
    when(dataStore.get("UNITTEST-CLIENT-ID scope1")).thenReturn(
        new StoredCredential(FAKE_CREDENTIAL));
    Credential cred = getCredential();
    assertThat(cred.getAccessToken()).isEqualTo(FAKE_CREDENTIAL.getAccessToken());
    assertThat(cred.getRefreshToken()).isEqualTo(FAKE_CREDENTIAL.getRefreshToken());
    assertThat(cred.getExpirationTimeMilliseconds()).isEqualTo(
        FAKE_CREDENTIAL.getExpirationTimeMilliseconds());
  }

  @Test
  public void test_provideCredential_notStored() {
    thrown.expect(AuthModule.LoginRequiredException.class);
    // Doing this without the mock setup should cause us to throw an exception because the
    // credential has not been stored.
    getCredential();
  }
}
