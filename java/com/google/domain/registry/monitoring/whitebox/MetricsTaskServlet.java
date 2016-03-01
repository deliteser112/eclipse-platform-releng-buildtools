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

package com.google.domain.registry.monitoring.whitebox;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.domain.registry.util.HttpServletUtils.getRequiredParameterValue;

import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.TableDataInsertAllRequest;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse.InsertErrors;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.domain.registry.bigquery.BigqueryFactory;
import com.google.domain.registry.bigquery.BigqueryHelper;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.util.FormattingLogger;
import com.google.domain.registry.util.NonFinalForTesting;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet for exporting metrics to BigQuery. */
public class MetricsTaskServlet extends HttpServlet {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();
  private static final String DATASET_ID = "metrics";
  private static final String PROJECT_ID = RegistryEnvironment.get().config().getProjectId();

  private static final Set<String> SPECIAL_PARAMS = ImmutableSet.of("tableId", "insertId");

  // Add any concrete Metric classes to this map or doPost() will throw IllegalArgumentException.
  private static final Map<String, ImmutableList<TableFieldSchema>> KNOWN_TABLE_SCHEMAS =
      ImmutableMap.of(EppMetrics.TABLE_ID, EppMetrics.SCHEMA_FIELDS);

  // servlet level cross-request caches to avoid unnecessary RPCs.
  @NonFinalForTesting
  private static Set<String> knownTables = Sets.newConcurrentHashSet();

  @NonFinalForTesting
  private static Set<String> datasets = Sets.newConcurrentHashSet();

  @NonFinalForTesting
  private static BigqueryFactory bigqueryFactory = new BigqueryFactory();

  @NonFinalForTesting
  private static BigqueryHelper bigqueryHelper = new BigqueryHelper();

  /** Returns a filtered {@link ImmutableMap} from an {@link HttpServletRequest} */
  private static ImmutableMap<String, Object> getFiteredMapFromRequest(
      HttpServletRequest req,
      Set<String> filter) {
    ImmutableMap.Builder<String, Object> b = new ImmutableMap.Builder<>();

    @SuppressWarnings({"cast", "unchecked"})  // Return type is always a Set<String>.
    Set<String> parameterKeys = (Set<String>) req.getParameterMap().keySet();

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
      ImmutableMap<String, Object> fields = getFiteredMapFromRequest(req, SPECIAL_PARAMS);


      final Bigquery bigquery = bigqueryFactory.create(
          getClass().getSimpleName(),
          new UrlFetchTransport(),
          new JacksonFactory(),
          new AppIdentityCredential(BigqueryScopes.all()));


      // Note: it's safe for multiple threads to call this as the dataset will
      // only be created once.
      if (!datasets.contains(DATASET_ID)) {
        bigqueryHelper.ensureDataset(bigquery, PROJECT_ID, DATASET_ID);
        datasets.add(DATASET_ID);
      }

      checkArgument(KNOWN_TABLE_SCHEMAS.containsKey(tableId), "Unknown table ID: %s", tableId);

      if (!knownTables.contains(tableId)) {
        bigqueryHelper.ensureTable(
            bigquery,
            new TableReference()
                .setDatasetId(DATASET_ID)
                .setProjectId(PROJECT_ID)
                .setTableId(tableId),
            KNOWN_TABLE_SCHEMAS.get(tableId));
        knownTables.add(tableId);
      }

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
