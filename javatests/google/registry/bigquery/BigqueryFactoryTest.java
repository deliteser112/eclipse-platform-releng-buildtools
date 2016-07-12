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

package google.registry.bigquery;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.bigquery.BigqueryUtils.FieldType.STRING;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.Table;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link BigqueryFactory}. */
@RunWith(MockitoJUnitRunner.class)
public class BigqueryFactoryTest {

  @Mock
  private BigqueryFactory.Subfactory subfactory;

  @Mock
  private Bigquery bigquery;

  @Mock
  private Bigquery.Datasets bigqueryDatasets;

  @Mock
  private Bigquery.Datasets.Insert bigqueryDatasetsInsert;

  @Mock
  private Bigquery.Tables bigqueryTables;

  @Mock
  private Bigquery.Tables.Insert bigqueryTablesInsert;

  private BigqueryFactory factory;

  @Before
  public void before() throws Exception {
    when(subfactory.create(
        anyString(),
        any(HttpTransport.class),
        any(JsonFactory.class),
        any(HttpRequestInitializer.class)))
            .thenReturn(bigquery);
    when(bigquery.datasets()).thenReturn(bigqueryDatasets);
    when(bigqueryDatasets.insert(eq("Project-Id"), any(Dataset.class)))
        .thenReturn(bigqueryDatasetsInsert);
    when(bigquery.tables()).thenReturn(bigqueryTables);
    when(bigqueryTables.insert(eq("Project-Id"), any(String.class), any(Table.class)))
        .thenReturn(bigqueryTablesInsert);
    factory = new BigqueryFactory();
    factory.subfactory = subfactory;
    factory.bigquerySchemas =
        new ImmutableMap.Builder<String, ImmutableList<TableFieldSchema>>()
            .put(
                "Table-Id",
                ImmutableList.of(new TableFieldSchema().setName("column1").setType(STRING.name())))
            .put(
                "Table2",
                ImmutableList.of(new TableFieldSchema().setName("column1").setType(STRING.name())))
            .build();
  }

  @Test
  public void testSuccess_datastoreCreation() throws Exception {
    factory.create("Project-Id", "Dataset-Id");

    ArgumentCaptor<Dataset> datasetArg = ArgumentCaptor.forClass(Dataset.class);
    verify(bigqueryDatasets).insert(eq("Project-Id"), datasetArg.capture());
    assertThat(datasetArg.getValue().getDatasetReference().getProjectId())
        .isEqualTo("Project-Id");
    assertThat(datasetArg.getValue().getDatasetReference().getDatasetId())
        .isEqualTo("Dataset-Id");
    verify(bigqueryDatasetsInsert).execute();
  }

  @Test
  public void testSuccess_datastoreAndTableCreation() throws Exception {
    factory.create("Project-Id", "Dataset2", "Table2");

    ArgumentCaptor<Dataset> datasetArg = ArgumentCaptor.forClass(Dataset.class);
    verify(bigqueryDatasets).insert(eq("Project-Id"), datasetArg.capture());
    assertThat(datasetArg.getValue().getDatasetReference().getProjectId())
        .isEqualTo("Project-Id");
    assertThat(datasetArg.getValue().getDatasetReference().getDatasetId())
        .isEqualTo("Dataset2");
    verify(bigqueryDatasetsInsert).execute();

    ArgumentCaptor<Table> tableArg = ArgumentCaptor.forClass(Table.class);
    verify(bigqueryTables).insert(eq("Project-Id"), eq("Dataset2"), tableArg.capture());
    TableReference ref = tableArg.getValue().getTableReference();
    assertThat(ref.getProjectId()).isEqualTo("Project-Id");
    assertThat(ref.getDatasetId()).isEqualTo("Dataset2");
    assertThat(ref.getTableId()).isEqualTo("Table2");
    assertThat(tableArg.getValue().getSchema().getFields())
        .containsExactly(new TableFieldSchema().setName("column1").setType(STRING.name()));
    verify(bigqueryTablesInsert).execute();
  }
}
