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

package com.google.domain.registry.export;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.html.HtmlEscapers.htmlEscaper;
import static com.google.domain.registry.util.HttpServletUtils.getRequiredParameterValue;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.Table;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.ViewDefinition;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.net.MediaType;
import com.google.domain.registry.bigquery.BigqueryFactory;
import com.google.domain.registry.bigquery.BigqueryHelper;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.util.FormattingLogger;
import com.google.domain.registry.util.NonFinalForTesting;
import com.google.domain.registry.util.SqlTemplate;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Update a well-known view to point at a certain datastore snapshot table in BigQuery. */
public class UpdateSnapshotViewServlet extends HttpServlet {

  private static final RegistryEnvironment ENVIRONMENT = RegistryEnvironment.get();

  /** Headers for passing parameters into the servlet. */
  static final String SNAPSHOT_DATASET_ID_PARAM = "dataset";
  static final String SNAPSHOT_TABLE_ID_PARAM = "table";
  static final String SNAPSHOT_KIND_PARAM = "kind";

  /** Servlet-specific details needed for enqueuing tasks against itself. */
  static final String QUEUE = "export-snapshot-update-view";  // See queue.xml.
  static final String PATH = "/_dr/task/updateSnapshotView";  // See web.xml.

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private static final BigqueryHelper bigqueryHelper = new BigqueryHelper();

  @NonFinalForTesting
  private static BigqueryFactory bigqueryFactory = new BigqueryFactory();

  /** Create a task for updating a snapshot view.  */
  public static TaskOptions createViewUpdateTask(
      String datasetId, String tableId, String kindName) {
    return TaskOptions.Builder.withUrl(PATH)
        .method(Method.POST)
        .param(SNAPSHOT_DATASET_ID_PARAM, datasetId)
        .param(SNAPSHOT_TABLE_ID_PARAM, tableId)
        .param(SNAPSHOT_KIND_PARAM, kindName);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    try {
      String datasetId = getRequiredParameterValue(req, SNAPSHOT_DATASET_ID_PARAM);
      String tableId = getRequiredParameterValue(req, SNAPSHOT_TABLE_ID_PARAM);
      String kindName = getRequiredParameterValue(req, SNAPSHOT_KIND_PARAM);

      String message = updateSnapshotView(datasetId, tableId, kindName);

      rsp.setStatus(SC_OK);
      rsp.setContentType(MediaType.PLAIN_TEXT_UTF_8.toString());
      rsp.getWriter().write("OK\n\n" + message);
    } catch (Throwable e) {
      logger.severe(e, e.toString());
      rsp.sendError(
          e instanceof IllegalArgumentException ? SC_BAD_REQUEST : SC_INTERNAL_SERVER_ERROR,
          htmlEscaper().escape(firstNonNull(e.getMessage(), e.toString())));
    }
  }

  private String updateSnapshotView(String datasetId, String tableId, String kindName)
      throws IOException {
    Bigquery bigquery = bigqueryFactory.create(
        getClass().getSimpleName(),
        new UrlFetchTransport(),
        new JacksonFactory(),
        new AppIdentityCredential(BigqueryScopes.all()));
    String projectId = ENVIRONMENT.config().getProjectId();

    bigqueryHelper.ensureDataset(
        bigquery, projectId, ENVIRONMENT.config().getLatestSnapshotDataset());

    updateTable(bigquery, new Table()
        .setTableReference(new TableReference()
            .setProjectId(projectId)
            .setDatasetId(ENVIRONMENT.config().getLatestSnapshotDataset())
            .setTableId(kindName))
        .setView(new ViewDefinition().setQuery(
            SqlTemplate.create("SELECT * FROM [%DATASET%.%TABLE%]")
                .put("DATASET", datasetId)
                .put("TABLE", tableId)
                .build())));

    String message = String.format(
        "Updated view %s:%s to point at snapshot table %s:%s.",
        ENVIRONMENT.config().getLatestSnapshotDataset(), kindName, datasetId, tableId);
    logger.info(message);
    return message;
  }

  private static void updateTable(Bigquery bigquery, Table table) throws IOException {
    TableReference ref = table.getTableReference();
    try {
      bigquery.tables()
          .update(ref.getProjectId(), ref.getDatasetId(), ref.getTableId(), table)
          .execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getDetails().getCode() == 404) {
        bigquery.tables().insert(ref.getProjectId(), ref.getDatasetId(), table).execute();
      }
    }
  }
}
