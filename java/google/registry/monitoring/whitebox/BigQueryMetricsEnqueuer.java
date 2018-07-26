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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.common.base.Supplier;
import com.google.common.flogger.FluentLogger;
import google.registry.util.AppEngineServiceUtils;
import java.util.Map.Entry;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * A collector of metric information. Enqueues collected metrics to a task queue to be written to
 * BigQuery asynchronously.
 *
 * @see MetricsExportAction
 */
public class BigQueryMetricsEnqueuer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String QUEUE_BIGQUERY_STREAMING_METRICS = "bigquery-streaming-metrics";

  @Inject AppEngineServiceUtils appEngineServiceUtils;
  @Inject @Named("insertIdGenerator") Supplier<String> idGenerator;
  @Inject @Named(QUEUE_BIGQUERY_STREAMING_METRICS) Queue queue;

  @Inject BigQueryMetricsEnqueuer() {}

  public void export(BigQueryMetric metric) {
    try {
      String hostname = appEngineServiceUtils.getCurrentVersionHostname("backend");
      TaskOptions opts =
          withUrl(MetricsExportAction.PATH)
              .header("Host", hostname)
              .param("insertId", idGenerator.get());
      for (Entry<String, String> entry : metric.getBigQueryRowEncoding().entrySet()) {
        opts.param(entry.getKey(), entry.getValue());
      }
      opts.param("tableId", metric.getTableId());
      queue.add(opts);
    } catch (TransientFailureException e) {
      // Log and swallow. We may drop some metrics here but this should be rare.
      logger.atInfo().withCause(e).log(
          "Transient error occurred while recording metric; metric dropped.");
    }
  }
}
