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

package google.registry.keyring.kms;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.CloudKMSScopes;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import java.util.Set;
import java.util.function.Function;

/** Dagger module for Cloud KMS connection objects. */
@Module
public abstract class KmsModule {

  @Provides
  static CloudKMS provideKms(
      HttpTransport transport,
      JsonFactory jsonFactory,
      Function<Set<String>, ? extends HttpRequestInitializer> credential,
      @Config("cloudKmsProjectId") String projectId) {
    return new CloudKMS.Builder(transport, jsonFactory, credential.apply(CloudKMSScopes.all()))
        .setApplicationName(projectId)
        .build();
  }

  @Binds
  abstract KmsConnection provideKmsConnection(KmsConnectionImpl kmsConnectionImpl);
}
