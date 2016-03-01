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

package com.google.domain.registry.bigquery;


import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.DatasetReference;
import com.google.api.services.bigquery.model.Table;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.common.collect.ImmutableList;
import com.google.domain.registry.util.FormattingLogger;

import java.io.IOException;

/** Helpers for Bigquery. */
public class BigqueryHelper {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * Ensures the dataset exists by trying to create it. Note that it's not appreciably cheaper
   * to check for dataset existence than it is to try to create it and check for exceptions.
   */
  // Note that these are not static so they can be mocked for testing.
  public void ensureDataset(Bigquery bigquery, String projectId, String datasetId)
      throws IOException {
    try {
      bigquery.datasets()
          .insert(projectId,
              new Dataset().setDatasetReference(
                  new DatasetReference()
                      .setProjectId(projectId)
                      .setDatasetId(datasetId)))
          .execute();
    } catch (IOException e) {
      // Swallow errors about a duplicate dataset, and throw any other ones.
      if (!BigqueryJobFailureException.create(e).getReason().equals("duplicate")) {
        throw e;
      }
    }
  }

  /** Ensures the table exists in Bigquery. */
  public void ensureTable(Bigquery bigquery, TableReference table,
      ImmutableList<TableFieldSchema> schema) throws IOException {
    try {
      bigquery.tables().insert(table.getProjectId(), table.getDatasetId(), new Table()
          .setSchema(new TableSchema().setFields(schema))
          .setTableReference(table))
          .execute();
      logger.infofmt("Created BigQuery table %s:%s.%s", table.getProjectId(), table.getDatasetId(),
          table.getTableId());
    } catch (IOException e) {
      // Swallow errors about a table that exists, and throw any other ones.
      if (!BigqueryJobFailureException.create(e).getReason().equals("duplicate")) {
        throw e;
      }
    }
  }
}
