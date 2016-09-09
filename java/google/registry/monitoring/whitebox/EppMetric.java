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

import static google.registry.bigquery.BigqueryUtils.toBigqueryTimestamp;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.bigquery.BigqueryUtils.FieldType;
import google.registry.model.eppoutput.Result.Code;
import google.registry.util.Clock;
import org.joda.time.DateTime;

/**
 * A value class for recording attributes of an EPP metric.
 *
 * @see BigQueryMetricsEnqueuer
 */
@AutoValue
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
            .put("startTime", toBigqueryTimestamp(getStartTimestamp()))
            .put("endTime", toBigqueryTimestamp(getEndTimestamp()))
            .put("attempts", getAttempts().toString());
    // Populate optional values, if present
    addOptional("commandName", getCommandName(), map);
    addOptional("clientId", getClientId(), map);
    addOptional("privilegeLevel", getPrivilegeLevel(), map);
    addOptional("eppTarget", getEppTarget(), map);
    if (getStatus().isPresent()) {
      map.put("eppStatus", Integer.toString(getStatus().get().code));
    }

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

  /** Create an {@link EppMetric.Builder}. */
  public static Builder builder() {
    return new AutoValue_EppMetric.Builder();
  }

  /**
   * Create an {@link EppMetric.Builder} for a request context, with the given request ID and
   * with start and end timestamps taken from the given clock.
   *
   * <p>The start timestamp is recorded now, and the end timestamp at {@code build()}.
   */
  public static Builder builderForRequest(String requestId, Clock clock) {
      return builder()
          .setRequestId(requestId)
          .setStartTimestamp(clock.nowUtc())
          .setClock(clock);
  }

  /** A builder to create instances of {@link EppMetric}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Builder-only counter of the number of attempts, to support {@link #incrementAttempts()}. */
    private int attempts = 0;

    /** Builder-only clock to support automatic recording of endTimestamp on {@link #build()}. */
    private Clock clock = null;

    abstract Builder setRequestId(String requestId);

    abstract Builder setStartTimestamp(DateTime startTimestamp);

    abstract Builder setEndTimestamp(DateTime endTimestamp);

    public abstract Builder setCommandName(String commandName);

    public abstract Builder setClientId(String clientId);

    public abstract Builder setClientId(Optional<String> clientId);

    public abstract Builder setPrivilegeLevel(String privilegeLevel);

    public abstract Builder setEppTarget(String eppTarget);

    public abstract Builder setStatus(Code code);

    abstract Builder setAttempts(Integer attempts);

    public Builder incrementAttempts() {
      attempts++;
      return this;
    }

    Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Build an instance of {@link EppMetric} using this builder.
     *
     * <p>If a clock was provided with {@code setClock()}, the end timestamp will be set to the
     * current timestamp of the clock; otherwise end timestamp must have been previously set.
     */
    public EppMetric build() {
      setAttempts(attempts);
      if (clock != null) {
        setEndTimestamp(clock.nowUtc());
      }
      return autoBuild();
    }

    abstract EppMetric autoBuild();
  }
}
