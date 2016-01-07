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

package google.registry.bigquery;


import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newConcurrentHashSet;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;

import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.DatasetReference;
import com.google.api.services.bigquery.model.Table;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.common.collect.ImmutableList;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** Factory for creating {@link Bigquery} connections. */
public class BigqueryFactory {

  private static final FormattingLogger logger = getLoggerForCallerClass();

  // Cross-request caches to avoid unnecessary RPCs.
  private static Set<String> knownExistingDatasets = newConcurrentHashSet();
  private static Set<String> knownExistingTables = newConcurrentHashSet();

  @Inject Map<String, ImmutableList<TableFieldSchema>> bigquerySchemas;
  @Inject Subfactory subfactory;
  @Inject BigqueryFactory() {}

  /** This class is broken out solely so that it can be mocked inside of tests. */
  static class Subfactory {

    @Inject Subfactory() {}

    public Bigquery create(
        String applicationName,
        HttpTransport transport,
        JsonFactory jsonFactory,
        HttpRequestInitializer httpRequestInitializer) {
      return new Bigquery.Builder(transport, jsonFactory, httpRequestInitializer)
          .setApplicationName(applicationName)
          .build();
    }
  }

  /** Returns a new connection to BigQuery. */
  public Bigquery create(
      String applicationName,
      HttpTransport transport,
      JsonFactory jsonFactory,
      HttpRequestInitializer httpRequestInitializer) {
    return subfactory.create(applicationName, transport, jsonFactory, httpRequestInitializer);
  }

  /**
   * Returns a new connection to Bigquery, first ensuring that the given dataset exists in the
   * project with the given id, creating it if required.
   */
  public Bigquery create(String projectId, String datasetId) throws IOException {
    Bigquery bigquery = create(
        getClass().getSimpleName(),
        new UrlFetchTransport(),
        new JacksonFactory(),
        new AppIdentityCredential(BigqueryScopes.all()));

    // Note: it's safe for multiple threads to call this as the dataset will only be created once.
    if (!knownExistingDatasets.contains(datasetId)) {
      ensureDataset(bigquery, projectId, datasetId);
      knownExistingDatasets.add(datasetId);
    }

    return bigquery;
  }

  /**
   * Returns a new connection to Bigquery, first ensuring that the given dataset and table exist in
   * project with the given id, creating them if required.
   */
  public Bigquery create(String projectId, String datasetId, String tableId)
      throws IOException {
    Bigquery bigquery = create(projectId, datasetId);
    checkArgument(bigquerySchemas.containsKey(tableId), "Unknown table ID: %s", tableId);

    if (!knownExistingTables.contains(tableId)) {
      ensureTable(
          bigquery,
          new TableReference()
              .setDatasetId(datasetId)
              .setProjectId(projectId)
              .setTableId(tableId),
            bigquerySchemas.get(tableId));
      knownExistingTables.add(tableId);
    }

    return bigquery;
  }

  /**
   * Ensures the dataset exists by trying to create it. Note that it's not appreciably cheaper
   * to check for dataset existence than it is to try to create it and check for exceptions.
   */
  // Note that these are not static so they can be mocked for testing.
  private void ensureDataset(Bigquery bigquery, String projectId, String datasetId)
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
  private void ensureTable(Bigquery bigquery, TableReference table, List<TableFieldSchema> schema)
      throws IOException {
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
