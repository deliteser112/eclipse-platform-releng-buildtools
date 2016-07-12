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

package google.registry.tools.server;

import static com.google.common.base.Strings.emptyToNull;
import static google.registry.request.RequestParameters.extractBooleanParameter;
import static google.registry.request.RequestParameters.extractOptionalParameter;
import static google.registry.request.RequestParameters.extractRequiredParameter;

import com.google.common.base.Optional;
import dagger.Module;
import dagger.Provides;
import google.registry.request.Parameter;
import javax.servlet.http.HttpServletRequest;

/**
 * Dagger module for the tools package.
 */
@Module
public class ToolsServerModule {

  @Provides
  @Parameter("clientId")
  static Optional<String> provideClientId(HttpServletRequest req) {
    return Optional.fromNullable(emptyToNull(req.getParameter(CreateGroupsAction.CLIENT_ID_PARAM)));
  }

  @Provides
  @Parameter("fields")
  static Optional<String> provideFields(HttpServletRequest req) {
    return extractOptionalParameter(req, ListObjectsAction.FIELDS_PARAM);
  }

  @Provides
  @Parameter("fullFieldNames")
  static Optional<Boolean> provideFullFieldNames(HttpServletRequest req) {
    String s = emptyToNull(req.getParameter(ListObjectsAction.FULL_FIELD_NAMES_PARAM));
    return (s == null) ? Optional.<Boolean>absent() : Optional.of(Boolean.parseBoolean(s));
  }

  @Provides
  @Parameter("inputData")
  static String provideInput(HttpServletRequest req) {
    return extractRequiredParameter(req, CreatePremiumListAction.INPUT_PARAM);
  }

  @Provides
  @Parameter("name")
  static String provideName(HttpServletRequest req) {
    return extractRequiredParameter(req, CreatePremiumListAction.NAME_PARAM);
  }

  @Provides
  @Parameter("override")
  static boolean provideOverride(HttpServletRequest req) {
   return extractBooleanParameter(req, CreatePremiumListAction.OVERRIDE_PARAM);
  }

  @Provides
  @Parameter("printHeaderRow")
  static Optional<Boolean> providePrintHeaderRow(HttpServletRequest req) {
    String s = emptyToNull(req.getParameter(ListObjectsAction.PRINT_HEADER_ROW_PARAM));
    return (s == null) ? Optional.<Boolean>absent() : Optional.of(Boolean.parseBoolean(s));
  }

  @Provides
  @Parameter("tld")
  static String provideTld(HttpServletRequest req) {
    return extractRequiredParameter(req, "tld");
  }

  @Provides
  @Parameter("rawKeys")
  static String provideRawKeys(HttpServletRequest req) {
    return extractRequiredParameter(req, "rawKeys");
  }
}
