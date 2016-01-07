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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.Bigquery.Tabledata;
import com.google.api.services.bigquery.Bigquery.Tabledata.InsertAll;
import com.google.api.services.bigquery.model.TableDataInsertAllRequest;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse.InsertErrors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import google.registry.bigquery.BigqueryFactory;
import google.registry.testing.AppEngineRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link MetricsExportAction}. */
@RunWith(MockitoJUnitRunner.class)
public class MetricsExportActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Mock
  BigqueryFactory bigqueryFactory;

  @Mock
  Bigquery bigquery;

  @Mock
  Tabledata tabledata;

  @Mock
  InsertAll insertAll;

  private TableDataInsertAllResponse response = new TableDataInsertAllResponse();
  private long currentTimeMillis = 1000000000000L;

  private ImmutableListMultimap<String, String> parameters =
      new ImmutableListMultimap.Builder<String, String>()
          .put("startTime", String.valueOf(MILLISECONDS.toSeconds(currentTimeMillis - 100)))
          .put("endTime", String.valueOf(MILLISECONDS.toSeconds(currentTimeMillis)))
          .put("jobname", "test job")
          .put("status", "success")
          .put("tld", "test")
          .build();

  MetricsExportAction action;

  @Before
  public void setup() throws Exception {
    when(bigqueryFactory.create(anyString(), anyString(), anyString())).thenReturn(bigquery);
    when(bigqueryFactory.create(
        anyString(),
        Matchers.any(HttpTransport.class),
        Matchers.any(JsonFactory.class),
        Matchers.any(HttpRequestInitializer.class)))
            .thenReturn(bigquery);

    when(bigquery.tabledata()).thenReturn(tabledata);
    when(tabledata.insertAll(
        anyString(),
        anyString(),
        anyString(),
        Matchers.any(TableDataInsertAllRequest.class))).thenReturn(insertAll);
    action = new MetricsExportAction();
    action.bigqueryFactory = bigqueryFactory;
    action.insertId = "insert id";
    action.parameters = parameters;
    action.projectId = "project id";
    action.tableId = "eppMetrics";
  }

  @Test
  public void testSuccess_nullErrors() throws Exception {
    when(insertAll.execute()).thenReturn(response);
    response.setInsertErrors(null);
    action.run();
    verify(insertAll).execute();
  }

  @Test
  public void testSuccess_emptyErrors() throws Exception {
    when(insertAll.execute()).thenReturn(response);
    response.setInsertErrors(ImmutableList.<InsertErrors>of());
    action.run();
    verify(insertAll).execute();
  }

  @Test
  public void testFailure_errors() throws Exception {
    when(insertAll.execute()).thenReturn(response);
    response.setInsertErrors(ImmutableList.of(new InsertErrors()));
    action.run();
  }
}
