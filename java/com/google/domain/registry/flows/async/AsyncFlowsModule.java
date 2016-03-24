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

package com.google.domain.registry.flows.async;

import static com.google.domain.registry.flows.async.DeleteEppResourceAction.PARAM_IS_SUPERUSER;
import static com.google.domain.registry.flows.async.DeleteEppResourceAction.PARAM_REQUESTING_CLIENT_ID;
import static com.google.domain.registry.flows.async.DeleteEppResourceAction.PARAM_RESOURCE_KEY;
import static com.google.domain.registry.flows.async.DnsRefreshForHostRenameAction.PARAM_HOST_KEY;
import static com.google.domain.registry.request.RequestParameters.extractBooleanParameter;
import static com.google.domain.registry.request.RequestParameters.extractRequiredParameter;

import com.google.domain.registry.request.Parameter;

import dagger.Module;
import dagger.Provides;

import javax.servlet.http.HttpServletRequest;

/** Dagger module for the async flows package. */
@Module
public final class AsyncFlowsModule {

  @Provides
  @Parameter(PARAM_IS_SUPERUSER)
  static boolean provideIsSuperuser(HttpServletRequest req) {
    return extractBooleanParameter(req, PARAM_IS_SUPERUSER);
  }

  @Provides
  @Parameter(PARAM_REQUESTING_CLIENT_ID)
  static String provideRequestingClientId(HttpServletRequest req) {
    return extractRequiredParameter(req, PARAM_REQUESTING_CLIENT_ID);
  }

  @Provides
  @Parameter(PARAM_RESOURCE_KEY)
  static String provideResourceKey(HttpServletRequest req) {
    return extractRequiredParameter(req, PARAM_RESOURCE_KEY);
  }

  @Provides
  @Parameter(PARAM_HOST_KEY)
  static String provideHostKey(HttpServletRequest req) {
    return extractRequiredParameter(req, PARAM_HOST_KEY);
  }
}
