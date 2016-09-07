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

package google.registry.monitoring.whitebox;

import static com.google.apphosting.api.ApiProxy.getCurrentEnvironment;
import static google.registry.bigquery.BigqueryUtils.toBigqueryTimestamp;
import static org.joda.time.DateTimeZone.UTC;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.bigquery.BigqueryUtils.FieldType;
import google.registry.model.eppoutput.Result.Code;
import google.registry.request.RequestScope;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * A value class for recording attributes of an EPP metric.
 *
 * @see BigQueryMetricsEnqueuer
 */
@AutoValue
@RequestScope
public abstract class EppMetric implements BigQueryMetric {

  static final String TABLE_ID = "eppMetrics";
  static final ImmutableList<TableFieldSchema> SCHEMA_FIELDS =
      ImmutableList.of(
          new TableFieldSchema().setName("requestId").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("startTime").setType(FieldType.TIMESTAMP.name()),
          new TableFieldSchema().setName("endTime").setType(FieldType.TIMESTAMP.name()),
          new TableFieldSchema().setName("commandName").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("clientId").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("privilegeLevel").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("eppTarget").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("eppStatus").setType(FieldType.INTEGER.name()),
          new TableFieldSchema().setName("attempts").setType(FieldType.INTEGER.name()));

  private static final String REQUEST_LOG_ID = "com.google.appengine.runtime.request_log_id";

  private static EppMetric create(
      String requestId,
      DateTime startTimestamp,
      DateTime endTimestamp,
      String commandName,
      String clientId,
      String privilegeLevel,
      String eppTarget,
      Code status,
      int attempts) {
    return new AutoValue_EppMetric(
        requestId,
        startTimestamp,
        endTimestamp,
        Optional.fromNullable(commandName),
        Optional.fromNullable(clientId),
        Optional.fromNullable(privilegeLevel),
        Optional.fromNullable(eppTarget),
        Optional.fromNullable(status),
        attempts);
  }

  public abstract String getRequestId();

  public abstract DateTime getStartTimestamp();

  public abstract DateTime getEndTimestamp();

  public abstract Optional<String> getCommandName();

  public abstract Optional<String> getClientId();

  public abstract Optional<String> getPrivilegeLevel();

  public abstract Optional<String> getEppTarget();

  public abstract Optional<Code> getStatus();

  public abstract Integer getAttempts();

  @Override
  public String getTableId() {
    return TABLE_ID;
  }

  @Override
  public ImmutableList<TableFieldSchema> getSchemaFields() {
    return SCHEMA_FIELDS;
  }

  @Override
  public ImmutableMap<String, String> getBigQueryRowEncoding() {
    // Create map builder, start with required values
    ImmutableMap.Builder<String, String> map =
        ImmutableMap.<String, String>builder()
            .put("requestId", getRequestId())
            .put(
                "startTime",
                toBigqueryTimestamp(getStartTimestamp().getMillis(), TimeUnit.MILLISECONDS))
            .put(
                "endTime",
                toBigqueryTimestamp(getEndTimestamp().getMillis(), TimeUnit.MILLISECONDS))
            .put("attempts", getAttempts().toString());
    // Populate optional values, if present
    addOptional("commandName", getCommandName(), map);
    addOptional("clientId", getClientId(), map);
    addOptional("privilegeLevel", getPrivilegeLevel(), map);
    addOptional("eppTarget", getEppTarget(), map);
    addOptional("status", getStatus(), map);

    return map.build();
  }

  /**
   * Helper method to populate an {@link com.google.common.collect.ImmutableMap.Builder} with an
   * {@link Optional} value if the value is {@link Optional#isPresent()}.
   */
  private static <T> void addOptional(
      String key, Optional<T> value, ImmutableMap.Builder<String, String> map) {
    if (value.isPresent()) {
      map.put(key, value.get().toString());
    }
  }

  /** A builder to create instances of {@link EppMetric}. */
  public static class Builder {

    // Required values
    private final String requestId;
    private final DateTime startTimestamp;
    private int attempts = 0;

    // Optional values
    private String commandName;
    private String clientId;
    private String privilegeLevel;
    private String eppTarget;
    private Code status;

    /**
     * Create an {@link EppMetric.Builder}.
     *
     * <p>The start timestamp of metrics created via this instance's {@link Builder#build()} will be
     * the time that this builder was created.
     */
    @Inject
    public Builder() {
      this(DateTime.now(UTC));
    }

    @VisibleForTesting
    Builder(DateTime startTimestamp) {
      this.requestId = getCurrentEnvironment().getAttributes().get(REQUEST_LOG_ID).toString();
      this.startTimestamp = startTimestamp;
      this.attempts = 0;
    }

    public Builder setCommandName(String value) {
      commandName = value;
      return this;
    }

    public Builder setClientId(String value) {
      clientId = value;
      return this;
    }

    public Builder setPrivilegeLevel(String value) {
      privilegeLevel = value;
      return this;
    }

    public Builder setEppTarget(String value) {
      eppTarget = value;
      return this;
    }

    public Builder setStatus(Code value) {
      status = value;
      return this;
    }

    public Builder incrementAttempts() {
      attempts++;
      return this;
    }

    @VisibleForTesting
    EppMetric build(DateTime endTimestamp) {
      return EppMetric.create(
          requestId,
          startTimestamp,
          endTimestamp,
          commandName,
          clientId,
          privilegeLevel,
          eppTarget,
          status,
          attempts);
    }

    /**
     * Build an instance of {@link EppMetric} using this builder.
     *
     * <p>The end timestamp of the metric will be the current time.
     */
    public EppMetric build() {
      return build(DateTime.now(UTC));
    }
  }
}
