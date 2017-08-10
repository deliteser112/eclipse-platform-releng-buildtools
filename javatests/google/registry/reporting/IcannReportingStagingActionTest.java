// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.reporting;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.GcsTestingUtils.readGcsFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableTable;
import com.google.common.util.concurrent.ListenableFuture;
import google.registry.bigquery.BigqueryConnection;
import google.registry.bigquery.BigqueryConnection.DestinationTable;
import google.registry.bigquery.BigqueryUtils.TableType;
import google.registry.gcs.GcsUtils;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link google.registry.reporting.IcannReportingStagingAction}.
 */
@RunWith(JUnit4.class)
public class IcannReportingStagingActionTest {

  BigqueryConnection bigquery = mock(BigqueryConnection.class);
  FakeResponse response = new FakeResponse();
  ActivityReportingQueryBuilder queryBuilder;
  GcsService gcsService = GcsServiceFactory.createGcsService();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withLocalModules()
      .build();

  private IcannReportingStagingAction createAction() {
    IcannReportingStagingAction action = new IcannReportingStagingAction();
    queryBuilder = new ActivityReportingQueryBuilder();
    queryBuilder.projectId = "test-project";
    queryBuilder.yearMonth = "2017-05";
    action.reportingBucket = "test-bucket";
    action.yearMonth = "2017-05";
    action.subdir = Optional.absent();
    action.queryBuilder = queryBuilder;
    action.bigquery = bigquery;
    action.gcsUtils = new GcsUtils(gcsService, 1024);
    action.response = response;
    return action;
  }

  @Test
  public void testRunSuccess() throws Exception {
    when(bigquery.query(any(String.class), any(DestinationTable.class))).thenReturn(fakeFuture());
    DestinationTable.Builder tableBuilder = new DestinationTable.Builder()
        .datasetId("testdataset")
        .type(TableType.TABLE)
        .name("tablename")
        .overwrite(true);
    when(bigquery.buildDestinationTable(any(String.class))).thenReturn(tableBuilder);

    ImmutableTable<Integer, TableFieldSchema, Object> reportTable =
        new ImmutableTable.Builder<Integer, TableFieldSchema, Object>()
            .put(1, new TableFieldSchema().setName("tld"), "fooTld")
            .put(1, new TableFieldSchema().setName("fooField"), "12")
            .put(1, new TableFieldSchema().setName("barField"), "34")
            .put(2, new TableFieldSchema().setName("tld"), "barTld")
            .put(2, new TableFieldSchema().setName("fooField"), "56")
            .put(2, new TableFieldSchema().setName("barField"), "78")
            .build();
    when(bigquery.queryToLocalTableSync(any(String.class))).thenReturn(reportTable);
    IcannReportingStagingAction action = createAction();
    action.run();

    String expectedReport1 = "fooField,barField\r\n12,34";
    String expectedReport2 = "fooField,barField\r\n56,78";
    byte[] generatedFile1 =
        readGcsFile(
            gcsService,
            new GcsFilename("test-bucket/icann/monthly/2017-05", "fooTld-activity-201705.csv"));
    assertThat(new String(generatedFile1, UTF_8)).isEqualTo(expectedReport1);
    byte[] generatedFile2 =
        readGcsFile(
            gcsService,
            new GcsFilename("test-bucket/icann/monthly/2017-05", "barTld-activity-201705.csv"));
    assertThat(new String(generatedFile2, UTF_8)).isEqualTo(expectedReport2);
  }

  private ListenableFuture<DestinationTable> fakeFuture() {
    return new ListenableFuture<DestinationTable>() {
      @Override
      public void addListener(Runnable runnable, Executor executor) {
        // No-op
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return false;
      }

      @Override
      public DestinationTable get() throws InterruptedException, ExecutionException {
        return null;
      }

      @Override
      public DestinationTable get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return null;
      }
    };
  }
}
