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

package google.registry.monitoring.whitebox;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static google.registry.bigquery.BigqueryUtils.toBigqueryTimestamp;

import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.common.base.Supplier;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import google.registry.util.SystemClock;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** A collector of metric information. */
public abstract class Metrics {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  public static final String QUEUE = "bigquery-streaming-metrics";

  @NonFinalForTesting
  private static ModulesService modulesService = ModulesServiceFactory.getModulesService();

  @NonFinalForTesting
  private static Clock clock = new SystemClock();

  @NonFinalForTesting
  private static Supplier<String> idGenerator =
      new Supplier<String>() {
        @Override
        public String get() {
          return UUID.randomUUID().toString();
        }};

  protected final Map<String, Object> fields = new HashMap<>();

  private final long startTimeMillis = clock.nowUtc().getMillis();

  public void setTableId(String tableId) {
    fields.put("tableId", tableId);
  }

  public void export() {
    try {
      String hostname = modulesService.getVersionHostname("backend", null);
      TaskOptions opts = withUrl(MetricsExportAction.PATH)
          .header("Host", hostname)
          .param("insertId", idGenerator.get())
          .param("startTime", toBigqueryTimestamp(startTimeMillis, TimeUnit.MILLISECONDS))
          .param("endTime", toBigqueryTimestamp(clock.nowUtc().getMillis(), TimeUnit.MILLISECONDS));
      for (Entry<String, Object> entry : fields.entrySet()) {
        opts.param(entry.getKey(), String.valueOf(entry.getValue()));
      }
      getQueue(QUEUE).add(opts);
    } catch (TransientFailureException e) {
      // Log and swallow. We may drop some metrics here but this should be rare.
      logger.info(e, e.getMessage());
    }
  }
}
