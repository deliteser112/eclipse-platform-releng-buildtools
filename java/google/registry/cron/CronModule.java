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

package google.registry.cron;

import static google.registry.request.RequestParameters.extractBooleanParameter;
import static google.registry.request.RequestParameters.extractOptionalIntParameter;
import static google.registry.request.RequestParameters.extractRequiredParameter;
import static google.registry.request.RequestParameters.extractSetOfParameters;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import google.registry.request.Parameter;
import javax.servlet.http.HttpServletRequest;

/** Dagger module for the cron package. */
@Module
public final class CronModule {

  @Provides
  @Parameter("endpoint")
  static String provideEndpoint(HttpServletRequest req) {
    return extractRequiredParameter(req, "endpoint");
  }

  @Provides
  @Parameter("exclude")
  static ImmutableSet<String> provideExcludes(HttpServletRequest req) {
    return extractSetOfParameters(req, "exclude");
  }

  @Provides
  @Parameter("queue")
  static String provideQueue(HttpServletRequest req) {
    return extractRequiredParameter(req, "queue");
  }

  @Provides
  @Parameter("runInEmpty")
  static boolean provideRunInEmpty(HttpServletRequest req) {
    return extractBooleanParameter(req, "runInEmpty");
  }

  @Provides
  @Parameter("forEachRealTld")
  static boolean provideForEachRealTld(HttpServletRequest req) {
    return extractBooleanParameter(req, "forEachRealTld");
  }

  @Provides
  @Parameter("forEachTestTld")
  static boolean provideForEachTestTld(HttpServletRequest req) {
    return extractBooleanParameter(req, "forEachTestTld");
  }

  @Provides
  @Parameter("jitterSeconds")
  static Optional<Integer> provideJitterSeconds(HttpServletRequest req) {
    return extractOptionalIntParameter(req, "jitterSeconds");
  }
}
