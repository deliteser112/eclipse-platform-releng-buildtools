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

package com.google.domain.registry.export;

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.domain.registry.bigquery.BigqueryUtils.FieldMode;
import com.google.domain.registry.bigquery.BigqueryUtils.FieldType;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Helper class which acts as a container for Bigquery table schemas. */
class BigquerySchemas {
  private BigquerySchemas() {}

  /** The fields in the "Logs" table. */
  enum LogsTableField {

    // These fields appear in nested APP_LOG_LINES records.
    LOG_LEVEL(FieldType.STRING, FieldMode.NULLABLE),
    LOG_MESSAGE(FieldType.STRING, FieldMode.NULLABLE),
    TIME(FieldType.TIMESTAMP, FieldMode.NULLABLE),

    // These fields appear in records at top level of the table.
    START_TIME(FieldType.TIMESTAMP, FieldMode.NULLABLE),
    END_TIME(FieldType.TIMESTAMP, FieldMode.NULLABLE),
    RELEASE(FieldType.STRING, FieldMode.NULLABLE),
    APPID(FieldType.STRING, FieldMode.NULLABLE),
    COST(FieldType.FLOAT, FieldMode.NULLABLE),
    EPP_CLIENT_ID(FieldType.STRING, FieldMode.NULLABLE),
    EPP_COMMAND(FieldType.STRING, FieldMode.NULLABLE),
    EPP_RESULT(FieldType.BOOLEAN, FieldMode.NULLABLE),
    EPP_TARGET(FieldType.STRING, FieldMode.REPEATED),
    EPP_TLD(FieldType.STRING, FieldMode.NULLABLE),
    HOST(FieldType.STRING, FieldMode.NULLABLE),
    HTTP_VERSION(FieldType.STRING, FieldMode.NULLABLE),
    INSTANCE_KEY(FieldType.STRING, FieldMode.NULLABLE),
    IP(FieldType.STRING, FieldMode.NULLABLE),
    LATENCY_USEC(FieldType.INTEGER, FieldMode.NULLABLE),
    MCYCLES(FieldType.INTEGER, FieldMode.NULLABLE),
    METHOD(FieldType.STRING, FieldMode.NULLABLE),
    MODULE_ID(FieldType.STRING, FieldMode.NULLABLE),
    NICKNAME(FieldType.STRING, FieldMode.NULLABLE),
    OFFSET(FieldType.STRING, FieldMode.NULLABLE),
    PENDING_TIME_USEC(FieldType.INTEGER, FieldMode.NULLABLE),
    REFERRER(FieldType.STRING, FieldMode.NULLABLE),
    REPLICA_INDEX(FieldType.INTEGER, FieldMode.NULLABLE),
    REQUEST_ID(FieldType.STRING, FieldMode.NULLABLE),
    RESOURCE(FieldType.STRING, FieldMode.NULLABLE),
    RESPONSE_SIZE(FieldType.INTEGER, FieldMode.NULLABLE),
    STATUS(FieldType.INTEGER, FieldMode.NULLABLE),
    TASK_NAME(FieldType.STRING, FieldMode.NULLABLE),
    TASK_QUEUE_NAME(FieldType.STRING, FieldMode.NULLABLE),
    URL_MAP_ENTRY(FieldType.STRING, FieldMode.NULLABLE),
    USER_AGENT(FieldType.STRING, FieldMode.NULLABLE),
    VERSION_ID(FieldType.STRING, FieldMode.NULLABLE),
    APP_LOG_LINES(FieldType.RECORD, FieldMode.REPEATED,
        ImmutableList.of(LOG_LEVEL, LOG_MESSAGE, TIME));

    private final FieldType fieldType;
    private final FieldMode fieldMode;
    private final ImmutableList<LogsTableField> childFields;

    LogsTableField(FieldType fieldType, FieldMode fieldMode) {
      this(fieldType, fieldMode, ImmutableList.<LogsTableField>of());
    }

    LogsTableField(
        FieldType fieldType, FieldMode fieldMode, ImmutableList<LogsTableField> childFields) {
      this.fieldType = checkNotNull(fieldType);
      this.fieldMode = checkNotNull(fieldMode);
      this.childFields = checkNotNull(childFields);
    }

    /** Return the name of the field as it should appear in the Bigquery schema. */
    String schemaName() {
      return UPPER_UNDERSCORE.to(LOWER_UNDERSCORE, name());
    }

    /** Return the {@link TableFieldSchema} of this field for use in a Bigquery table. */
    private TableFieldSchema getTableFieldSchema() {
      TableFieldSchema tableFieldSchema = new TableFieldSchema()
          .setName(schemaName())
          .setType(fieldType.schemaName())
          .setMode(fieldMode.schemaName());
      if (!childFields.isEmpty()) {
        tableFieldSchema.setFields(getSchema(childFields));
      }
      return tableFieldSchema;
    }

    /**
     * Return the schema of a list of {@link TableFieldSchema} objects for use in a Bigquery table.
     */
    private static List<TableFieldSchema> getSchema(Iterable<LogsTableField> fields) {
      return FluentIterable.from(fields)
          .transform(new Function<LogsTableField, TableFieldSchema>() {
            @Override
            public TableFieldSchema apply(LogsTableField field) {
              return field.getTableFieldSchema();
            }})
          .toList();
    }

    /**
     * Return the schema of this table for use in a Bigquery table.
     */
    static List<TableFieldSchema> getTableSchema() {
      List<LogsTableField> allFields = Arrays.asList(LogsTableField.values());

      // Collect the list of all child fields so we can exclude them from the list of fields at the
      // top level of the schema.
      Set<LogsTableField> childFields = FluentIterable.from(allFields)
          .transformAndConcat(new Function<LogsTableField, List<LogsTableField>>() {
            @Override
            public List<LogsTableField> apply(LogsTableField field) {
              return field.childFields;
            }})
          .toSet();

      Set<LogsTableField> topLevelFields =
          Sets.difference(ImmutableSet.copyOf(allFields), childFields);
      return getSchema(topLevelFields);
    }
  }
}
