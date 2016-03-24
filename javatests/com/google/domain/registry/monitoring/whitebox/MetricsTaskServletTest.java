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

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
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
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.bigquery.BigqueryFactory;
import com.google.domain.registry.bigquery.BigqueryHelper;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.InjectRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Unit tests for {@link MetricsTaskServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class MetricsTaskServletTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Mock
  BigqueryFactory bigqueryFactory;

  @Mock
  BigqueryHelper bigqueryHelper;

  @Mock
  Bigquery bigquery;

  @Mock
  Tabledata tabledata;

  @Mock
  InsertAll insertAll;

  @Mock
  private HttpServletRequest req;

  @Mock
  private HttpServletResponse rsp;

  private final StringWriter httpOutput = new StringWriter();

  TableDataInsertAllResponse response = new TableDataInsertAllResponse();
  long currentTimeMillis = 1000000000000L;

  Map<String, Object> params = new ImmutableMap.Builder<String, Object>()
      .put("tableId", "eppMetrics")
      .put("insertId", "insert id")
      .put("startTime", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis - 100)))
      .put("endTime", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis)))
      .put("jobname", "test job")
      .put("status", "success")
      .put("tld",  "test").build();

  MetricsTaskServlet servlet;

  @Before
  public void setup() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getParameterMap()).thenReturn(params);

    for (String key : params.keySet()) {
      when(req.getParameter(key)).thenReturn((String) params.get(key));
    }

    when(rsp.getWriter()).thenReturn(new PrintWriter(httpOutput));

    inject.setStaticField(MetricsTaskServlet.class, "bigqueryFactory", bigqueryFactory);
    when(bigqueryFactory.create(
        anyString(),
        Matchers.any(HttpTransport.class),
        Matchers.any(JsonFactory.class),
        Matchers.any(HttpRequestInitializer.class)))
            .thenReturn(bigquery);

    inject.setStaticField(MetricsTaskServlet.class, "bigqueryHelper", bigqueryHelper);
    doNothing().when(bigqueryHelper).ensureDataset(Matchers.any(Bigquery.class), anyString(),
        anyString());
    doNothing().when(bigqueryHelper).ensureTable(
        Matchers.any(Bigquery.class),
        Matchers.any(TableReference.class),
        Matchers.<ImmutableList<TableFieldSchema>>any());

    when(bigquery.tabledata()).thenReturn(tabledata);
    when(tabledata.insertAll(
        anyString(),
        anyString(),
        anyString(),
        Matchers.any(TableDataInsertAllRequest.class))).thenReturn(insertAll);
    servlet = new MetricsTaskServlet();
  }

  @Test
  public void testSuccess_nullErrors() throws Exception {
    when(insertAll.execute()).thenReturn(response);
    response.setInsertErrors(null);
    servlet.service(req, rsp);
    verify(insertAll).execute();
  }

  @Test
  public void testSuccess_emptyErrors() throws Exception {
    when(insertAll.execute()).thenReturn(response);
    response.setInsertErrors(ImmutableList.<InsertErrors>of());
    servlet.service(req, rsp);
    verify(insertAll).execute();
  }

  @Test
  public void testFailure_errors() throws Exception {
    when(insertAll.execute()).thenReturn(response);
    response.setInsertErrors(ImmutableList.of(new InsertErrors()));
    servlet.service(req, rsp);
  }
}
