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

package google.registry.batch;

import static com.google.api.client.util.Data.NULL_STRING;
import static google.registry.batch.EntityIntegrityAlertsSchema.DATASET;
import static google.registry.batch.EntityIntegrityAlertsSchema.FIELD_MESSAGE;
import static google.registry.batch.EntityIntegrityAlertsSchema.FIELD_SCANTIME;
import static google.registry.batch.EntityIntegrityAlertsSchema.FIELD_SOURCE;
import static google.registry.batch.EntityIntegrityAlertsSchema.FIELD_TARGET;
import static google.registry.batch.EntityIntegrityAlertsSchema.TABLE_ID;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.Bigquery.Tabledata.InsertAll;
import com.google.api.services.bigquery.model.TableDataInsertAllRequest;
import com.google.api.services.bigquery.model.TableDataInsertAllRequest.Rows;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse.InsertErrors;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.bigquery.BigqueryFactory;
import google.registry.config.RegistryConfig.Config;
import google.registry.util.Retrier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import javax.inject.Named;
import org.joda.time.DateTime;

/**
 * An injected utility class used to check entity integrity and stream violations to BigQuery.
 */
@AutoFactory(allowSubclasses = true)
public class VerifyEntityIntegrityStreamer {

  private final String projectId;
  private final BigqueryFactory bigqueryFactory;
  private final Supplier<String> idGenerator;
  private final Retrier retrier;
  private final DateTime scanTime;
  private Bigquery bigquery;

  public VerifyEntityIntegrityStreamer(
      @Provided @Config("projectId") String projectId,
      @Provided BigqueryFactory bigqueryFactory,
      @Provided Retrier retrier,
      @Provided @Named("insertIdGenerator") Supplier<String> idGenerator,
      DateTime scanTime) {
    this.projectId = projectId;
    this.bigqueryFactory = bigqueryFactory;
    this.retrier = retrier;
    this.idGenerator = idGenerator;
    this.scanTime = scanTime;
  }

  // This is lazily loaded so we only construct the connection when needed, as in a healthy
  // Datastore almost every single check should short-circuit return and won't output anything to
  // BigQuery.
  private Bigquery getBigquery() throws IOException {
    if (bigquery == null) {
      bigquery = bigqueryFactory.create(projectId, DATASET, TABLE_ID);
    }
    return bigquery;
  }

  /**
   * Check that the given conditional holds, and if not, stream the supplied source, target, and
   * message information to BigQuery.
   *
   * @return Whether the check succeeded.
   */
  boolean check(
      boolean conditional,
      @Nullable Object source,
      @Nullable Object target,
      @Nullable String message) {
    return checkOneToMany(
        conditional, source, ImmutableList.of((target == null) ? NULL_STRING : target), message);
  }

  /**
   * Check that the given conditional holds, and if not, stream a separate row to BigQuery for each
   * supplied target (the source and message will be the same for each).
   *
   * @return Whether the check succeeded.
   */
  <T> boolean checkOneToMany(
      boolean conditional,
      @Nullable Object source,
      Iterable<T> targets,
      @Nullable String message) {
    return checkManyToMany(
        conditional, ImmutableList.of((source == null) ? NULL_STRING : source), targets, message);
  }

  /**
   * Check that the given conditional holds, and if not, stream a separate row to BigQuery for each
   * supplied target (the source and message will be the same for each).
   *
   * @return Whether the check succeeded.
   */
  <S> boolean checkManyToOne(
      boolean conditional,
      Iterable<S> sources,
      @Nullable Object target,
      @Nullable String message) {
    return checkManyToMany(
        conditional, sources, ImmutableList.of((target == null) ? NULL_STRING : target), message);
  }


  /**
   * Check that the given conditional holds, and if not, stream a separate row to BigQuery for the
   * cross product of every supplied target and source (the message will be the same for each).
   * This is used in preference to records (repeated fields) in BigQuery because they are
   * significantly harder to work with.
   *
   * @return Whether the check succeeded.
   */
  private <S, T> boolean checkManyToMany(
      boolean conditional,
      Iterable<S> sources,
      Iterable<T> targets,
      @Nullable String message) {
    if (conditional) {
      return true;
    }
    ImmutableList.Builder<Rows> rows = new ImmutableList.Builder<>();
    for (S source : sources) {
      for (T target : targets) {
        Map<String, Object> rowData =
            new ImmutableMap.Builder<String, Object>()
                .put(
                    FIELD_SCANTIME,
                    new com.google.api.client.util.DateTime(scanTime.toDate()))
                .put(
                    FIELD_SOURCE,
                    source.toString())
                .put(
                    FIELD_TARGET,
                    target.toString())
                .put(
                    FIELD_MESSAGE,
                    (message == null) ? NULL_STRING : message)
                .build();
        rows.add(
            new TableDataInsertAllRequest.Rows().setJson(rowData).setInsertId(idGenerator.get()));
      }
    }
    streamToBigqueryWithRetry(rows.build());
    return false;
  }

  private void streamToBigqueryWithRetry(List<Rows> rows) {
    try {
      final InsertAll request =
          getBigquery()
              .tabledata()
              .insertAll(
                  projectId,
                  DATASET,
                  TABLE_ID,
                  new TableDataInsertAllRequest().setRows(rows));

      Callable<Void> callable =
          new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              TableDataInsertAllResponse response = request.execute();
              // Turn errors on the response object into RuntimeExceptions that the retrier will
              // retry.
              if (response.getInsertErrors() != null && !response.getInsertErrors().isEmpty()) {
                throw new RuntimeException(
                    FluentIterable.from(response.getInsertErrors())
                        .transform(
                            new Function<InsertErrors, String>() {
                              @Override
                              public String apply(InsertErrors error) {
                                try {
                                  return error.toPrettyString();
                                } catch (IOException e) {
                                  return error.toString();
                                }
                              }
                            })
                        .join(Joiner.on('\n')));
              }
              return null;
            }
          };
      retrier.callWithRetry(callable, RuntimeException.class);
    } catch (IOException e) {
      throw new RuntimeException("Error sending integrity error to BigQuery", e);
    }
  }
}

