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

package com.google.domain.registry.loadtest;

import static com.google.domain.registry.request.RequestParameters.extractIntParameter;
import static com.google.domain.registry.request.RequestParameters.extractRequiredParameter;

import com.google.domain.registry.request.Parameter;

import dagger.Module;
import dagger.Provides;

import javax.servlet.http.HttpServletRequest;

/**
 * Dagger module for loadtest package.
 *
 * @see "com.google.domain.registry.module.backend.ToolsComponent"
 */
@Module
public final class LoadTestModule {

  // There's already an @Parameter("clientId") for CreateGroupsAction that's only optional, and we
  // want this one to be required, so give it a different name.
  @Provides
  @Parameter("loadtestClientId")
  static String provideClientId(HttpServletRequest req) {
    return extractRequiredParameter(req, "clientId");
  }

  @Provides
  @Parameter("delaySeconds")
  static int provideDelaySeconds(HttpServletRequest req) {
    return extractIntParameter(req, "delaySeconds");
  }

  @Provides
  @Parameter("runSeconds")
  static int provideRunSeconds(HttpServletRequest req) {
    return extractIntParameter(req, "runSeconds");
  }

  @Provides
  @Parameter("successfulDomainCreates")
  static int provideSuccessfulDomainCreates(HttpServletRequest req) {
    return extractIntParameter(req, "successfulDomainCreates");
  }

  @Provides
  @Parameter("failedDomainCreates")
  static int provideFailedDomainCreates(HttpServletRequest req) {
    return extractIntParameter(req, "failedDomainCreates");
  }

  @Provides
  @Parameter("domainInfos")
  static int provideDomainInfos(HttpServletRequest req) {
    return extractIntParameter(req, "domainInfos");
  }

  @Provides
  @Parameter("domainChecks")
  static int provideDomainChecks(HttpServletRequest req) {
    return extractIntParameter(req, "domainChecks");
  }

  @Provides
  @Parameter("successfulContactCreates")
  static int provideSuccessfulContactCreates(HttpServletRequest req) {
    return extractIntParameter(req, "successfulContactCreates");
  }

  @Provides
  @Parameter("failedContactCreates")
  static int provideFailedContactCreates(HttpServletRequest req) {
    return extractIntParameter(req, "failedContactCreates");
  }

  @Provides
  @Parameter("contactInfos")
  static int provideContactInfos(HttpServletRequest req) {
    return extractIntParameter(req, "contactInfos");
  }

  @Provides
  @Parameter("successfulHostCreates")
  static int provideSuccessfulHostCreates(HttpServletRequest req) {
    return extractIntParameter(req, "successfulHostCreates");
  }

  @Provides
  @Parameter("failedHostCreates")
  static int provideFailedHostCreates(HttpServletRequest req) {
    return extractIntParameter(req, "failedHostCreates");
  }

  @Provides
  @Parameter("hostInfos")
  static int provideHostInfos(HttpServletRequest req) {
    return extractIntParameter(req, "hostInfos");
  }
}
