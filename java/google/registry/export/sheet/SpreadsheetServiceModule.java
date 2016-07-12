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

package google.registry.export.sheet;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.common.collect.ImmutableList;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import dagger.Module;
import dagger.Provides;

/** Dagger module for {@link SpreadsheetService}. */
@Module
public final class SpreadsheetServiceModule {

  private static final String APPLICATION_NAME = "google-registry-v1";
  private static final ImmutableList<String> SCOPES = ImmutableList.of(
      "https://spreadsheets.google.com/feeds",
      "https://docs.google.com/feeds");

  @Provides
  static SpreadsheetService provideSpreadsheetService(GoogleCredential credential) {
    SpreadsheetService service = new SpreadsheetService(APPLICATION_NAME);
    service.setOAuth2Credentials(credential.createScoped(SCOPES));
    return service;
  }
}
