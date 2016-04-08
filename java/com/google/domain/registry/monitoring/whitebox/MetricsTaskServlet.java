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

package com.google.domain.registry.monitoring.whitebox;

import static com.google.domain.registry.util.HttpServletUtils.getRequiredParameterValue;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.TableDataInsertAllRequest;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse.InsertErrors;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.domain.registry.bigquery.BigqueryFactory;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.util.FormattingLogger;
import com.google.domain.registry.util.NonFinalForTesting;

import java.io.IOException;
import java.util.Set;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet for exporting metrics to BigQuery. */
public class MetricsTaskServlet extends HttpServlet {

  public static final String PATH = "/_dr/task/metrics";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();
  private static final String DATASET_ID = "metrics";
  private static final String PROJECT_ID = RegistryEnvironment.get().config().getProjectId();

  private static final Set<String> SPECIAL_PARAMS = ImmutableSet.of("tableId", "insertId");

  @NonFinalForTesting
  private static BigqueryFactory bigqueryFactory = new BigqueryFactory();

  /** Returns a filtered {@link ImmutableMap} from an {@link HttpServletRequest} */
  private static ImmutableMap<String, Object> getFilteredMapFromRequest(
      HttpServletRequest req,
      Set<String> filter) {
    ImmutableMap.Builder<String, Object> b = new ImmutableMap.Builder<>();

    @SuppressWarnings({"cast", "unchecked"})  // Return type is always a Set<String>.
    Set<String> parameterKeys = req.getParameterMap().keySet();

    for (String key : Sets.difference(parameterKeys, filter)) {
      b.put(key, req.getParameter(key));
    }

    return b.build();
  }

  /** Exports metrics to BigQuery. */
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    try {
      final String tableId = getRequiredParameterValue(req, "tableId");
      ImmutableMap<String, Object> fields = getFilteredMapFromRequest(req, SPECIAL_PARAMS);
      Bigquery bigquery = bigqueryFactory.create(PROJECT_ID, DATASET_ID, tableId);

      TableDataInsertAllResponse response = bigquery.tabledata()
          .insertAll(
              PROJECT_ID,
              DATASET_ID,
              tableId,
              new TableDataInsertAllRequest()
                  .setRows(
                      ImmutableList.of(new TableDataInsertAllRequest.Rows()
                          .setInsertId(req.getParameter("insertId"))
                          .setJson(fields))))
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
