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

package google.registry.monitoring.whitebox;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static google.registry.monitoring.whitebox.BigQueryMetricsEnqueuer.QUEUE_BIGQUERY_STREAMING_METRICS;
import static google.registry.request.RequestParameters.extractRequiredParameter;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.appengine.api.taskqueue.Queue;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import java.util.UUID;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

/**
 * Dagger module for injecting common settings for Whitebox tasks.
 */
@Module
public class WhiteboxModule {

  private static final String REQUEST_LOG_ID = "com.google.appengine.runtime.request_log_id";

  @Provides
  @IntoMap
  @StringKey(EppMetric.TABLE_ID)
  static ImmutableList<TableFieldSchema> provideEppMetricsSchema() {
    return EppMetric.SCHEMA_FIELDS;
  }

  @Provides
  @Parameter("tableId")
  static String provideTableId(HttpServletRequest req) {
    return extractRequiredParameter(req, "tableId");
  }

  @Provides
  @Parameter("insertId")
  static String provideInsertId(HttpServletRequest req) {
    return extractRequiredParameter(req, "insertId");
  }

  @Provides
  @Named("insertIdGenerator")
  static Supplier<String> provideInsertIdGenerator() {
    return new Supplier<String>() {
      @Override
      public String get() {
        return UUID.randomUUID().toString();
      }
    };
  }

  @Provides
  @Named("requestLogId")
  static String provideRequestLogId() {
    return ApiProxy.getCurrentEnvironment().getAttributes().get(REQUEST_LOG_ID).toString();
  }

  /** Provides an EppMetric builder with the request ID and startTimestamp already initialized. */
  @Provides
  static EppMetric.Builder provideEppMetricBuilder(
      @Named("requestLogId") String requestLogId, Clock clock) {
    return EppMetric.builderForRequest(requestLogId, clock);
  }

  @Provides
  @Named(QUEUE_BIGQUERY_STREAMING_METRICS)
  static Queue provideBigQueryStreamingMetricsQueue() {
    return getQueue(QUEUE_BIGQUERY_STREAMING_METRICS);
  }
}
