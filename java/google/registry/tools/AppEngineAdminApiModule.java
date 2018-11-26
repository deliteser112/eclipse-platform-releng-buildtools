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

package google.registry.tools;

import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.appengine.v1.Appengine;
import dagger.Module;
import dagger.Provides;
import google.registry.config.CredentialModule.AppEngineAdminApiCredential;
import google.registry.config.RegistryConfig.Config;
import javax.inject.Singleton;

/** Module providing the instance of {@link Appengine} to access App Engine Admin Api. */
@Module
public abstract class AppEngineAdminApiModule {

  @Provides
  @Singleton
  public static Appengine provideAppengine(
      @AppEngineAdminApiCredential AppIdentityCredential appIdentityCredential,
      @Config("projectId") String projectId) {
    return new Appengine.Builder(
            Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), appIdentityCredential)
        .setApplicationName(projectId)
        .build();
  }
}
