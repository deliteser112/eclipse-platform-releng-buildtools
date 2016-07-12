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

package google.registry.export;
import static google.registry.request.Action.Method.POST;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Table;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.ViewDefinition;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import google.registry.bigquery.BigqueryFactory;
import google.registry.config.ConfigModule.Config;
import google.registry.request.Action;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.Parameter;
import google.registry.util.FormattingLogger;
import google.registry.util.SqlTemplate;
import java.io.IOException;
import javax.inject.Inject;

/** Update a well-known view to point at a certain datastore snapshot table in BigQuery. */
@Action(path = UpdateSnapshotViewAction.PATH, method = POST)
public class UpdateSnapshotViewAction implements Runnable {

  /** Headers for passing parameters into the servlet. */
  static final String UPDATE_SNAPSHOT_DATASET_ID_PARAM = "dataset";
  static final String UPDATE_SNAPSHOT_TABLE_ID_PARAM = "table";
  static final String UPDATE_SNAPSHOT_KIND_PARAM = "kind";

  static final String LATEST_SNAPSHOT_DATASET = "latest_snapshot";

  /** Servlet-specific details needed for enqueuing tasks against itself. */
  static final String QUEUE = "export-snapshot-update-view";  // See queue.xml.
  static final String PATH = "/_dr/task/updateSnapshotView";  // See web.xml.

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Parameter(UPDATE_SNAPSHOT_DATASET_ID_PARAM) String datasetId;
  @Inject @Parameter(UPDATE_SNAPSHOT_TABLE_ID_PARAM) String tableId;
  @Inject @Parameter(UPDATE_SNAPSHOT_KIND_PARAM) String kindName;
  @Inject @Config("projectId") String projectId;
  @Inject BigqueryFactory bigqueryFactory;
  @Inject UpdateSnapshotViewAction() {}

  /** Create a task for updating a snapshot view.  */
  public static TaskOptions createViewUpdateTask(
      String datasetId, String tableId, String kindName) {
    return TaskOptions.Builder.withUrl(PATH)
        .method(Method.POST)
        .param(UPDATE_SNAPSHOT_DATASET_ID_PARAM, datasetId)
        .param(UPDATE_SNAPSHOT_TABLE_ID_PARAM, tableId)
        .param(UPDATE_SNAPSHOT_KIND_PARAM, kindName);
  }

  @Override
  public void run() {
    try {
      updateSnapshotView(datasetId, tableId, kindName);
    } catch (Throwable e) {
      logger.severefmt(e, "Could not update snapshot view for table %s", tableId);
      throw new InternalServerErrorException("Error in update snapshot view action");
    }
  }

  private void updateSnapshotView(String datasetId, String tableId, String kindName)
      throws IOException {
    Bigquery bigquery = bigqueryFactory.create(projectId, LATEST_SNAPSHOT_DATASET);

    updateTable(bigquery, new Table()
        .setTableReference(new TableReference()
            .setProjectId(projectId)
            .setDatasetId(LATEST_SNAPSHOT_DATASET)
            .setTableId(kindName))
        .setView(new ViewDefinition().setQuery(
            SqlTemplate.create("SELECT * FROM [%PROJECT%:%DATASET%.%TABLE%]")
                .put("PROJECT", projectId)
                .put("DATASET", datasetId)
                .put("TABLE", tableId)
                .build())));

    logger.infofmt(
        "Updated view %s to point at snapshot table %s.",
        String.format("[%s:%s.%s]", projectId, LATEST_SNAPSHOT_DATASET, kindName),
        String.format("[%s:%s.%s]", projectId, datasetId, tableId));
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
