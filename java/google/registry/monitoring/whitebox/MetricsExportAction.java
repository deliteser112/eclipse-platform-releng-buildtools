// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Multimaps.filterKeys;
import static google.registry.request.Action.Method.POST;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.TableDataInsertAllRequest;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse.InsertErrors;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.bigquery.BigqueryFactory;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.ParameterMap;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** Action for exporting metrics to BigQuery. */
@Action(path = MetricsExportAction.PATH, method = POST)
public class MetricsExportAction implements Runnable {

  public static final String PATH = "/_dr/task/metrics";
  private static final FormattingLogger logger = getLoggerForCallerClass();
  private static final String DATASET_ID = "metrics";
  private static final Set<String> SPECIAL_PARAMS = ImmutableSet.of("tableId", "insertId");

  @Inject @Parameter("tableId") String tableId;
  @Inject @Parameter("insertId") String insertId;
  @Inject @Config("projectId") String projectId;
  @Inject BigqueryFactory bigqueryFactory;
  @Inject @ParameterMap ImmutableListMultimap<String, String> parameters;
  @Inject MetricsExportAction() {}

  /** Exports metrics to BigQuery. */
  @Override
  public void run() {
    try {
      Bigquery bigquery = bigqueryFactory.create(projectId, DATASET_ID, tableId);
      // Filter out the special parameters that the Action is called with.  Everything that's left
      // is returned in a Map that is suitable to pass to Bigquery as row data.
      Map<String, Object> jsonRows =
          ImmutableMap.<String, Object>copyOf(
              filterKeys(parameters, not(in(SPECIAL_PARAMS))).entries());
      TableDataInsertAllResponse response = bigquery.tabledata()
          .insertAll(
              projectId,
              DATASET_ID,
              tableId,
              new TableDataInsertAllRequest()
                  .setRows(
                      ImmutableList.of(new TableDataInsertAllRequest.Rows()
                          .setInsertId(insertId)
                          .setJson(jsonRows))))
          .execute();

      if (response.getInsertErrors() != null && !response.getInsertErrors().isEmpty()) {
        throw new RuntimeException(FluentIterable
            .from(response.getInsertErrors())
            .transform(new Function<InsertErrors, String>() {
              @Override
              public String apply(InsertErrors error) {
                try {
                  return error.toPrettyString();
                } catch (IOException e) {
                  return error.toString();
                }
              }})
            .join(Joiner.on('\n')));
      }
    } catch (Throwable e) {
      logger.warningfmt("Caught Unknown Exception: %s", e);
    }
  }
}
