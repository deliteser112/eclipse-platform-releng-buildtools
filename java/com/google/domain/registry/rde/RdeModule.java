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

package com.google.domain.registry.rde;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;

import com.google.appengine.api.taskqueue.Queue;
import com.google.domain.registry.request.Parameter;
import com.google.domain.registry.request.RequestParameters;

import dagger.Module;
import dagger.Provides;

import org.joda.time.DateTime;

import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

/**
 * Dagger module for RDE package.
 *
 * @see "com.google.domain.registry.module.backend.BackendComponent"
 */
@Module
public final class RdeModule {

  static final String PARAM_WATERMARK = "watermark";

  @Provides
  @Parameter(PARAM_WATERMARK)
  static DateTime provideWatermark(HttpServletRequest req) {
    return DateTime.parse(RequestParameters.extractRequiredParameter(req, PARAM_WATERMARK));
  }

  @Provides
  @Named("brda")
  static Queue provideQueueBrda() {
    return getQueue("brda");
  }

  @Provides
  @Named("rde-report")
  static Queue provideQueueRdeReport() {
    return getQueue("rde-report");
  }

  @Provides
  @Named("rde-staging")
  static Queue provideQueueRdeStaging() {
    return getQueue("rde-staging");
  }

  @Provides
  @Named("rde-upload")
  static Queue provideQueueRdeUpload() {
    return getQueue("rde-upload");
  }
}
