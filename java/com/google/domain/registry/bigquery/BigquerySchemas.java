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

package com.google.domain.registry.bigquery;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.bigquery.BigqueryUtils.FieldType;
import com.google.domain.registry.util.NonFinalForTesting;

import java.util.Map;

/** Schemas for BigQuery tables. */
public final class BigquerySchemas {

  public static final String EPPMETRICS_TABLE_ID = "eppMetrics";
  public static final String ENTITY_INTEGRITY_ALERTS_TABLE_ID = "alerts";
  public static final String ENTITY_INTEGRITY_ALERTS_FIELD_SCANTIME = "scanTime";
  public static final String ENTITY_INTEGRITY_ALERTS_FIELD_SOURCE = "source";
  public static final String ENTITY_INTEGRITY_ALERTS_FIELD_TARGET = "target";
  public static final String ENTITY_INTEGRITY_ALERTS_FIELD_MESSAGE = "message";

  static final ImmutableList<TableFieldSchema> EPPMETRICS_SCHEMA_FIELDS =
      ImmutableList.<TableFieldSchema>of(
          new TableFieldSchema().setName("requestId").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("startTime").setType(FieldType.TIMESTAMP.name()),
          new TableFieldSchema().setName("endTime").setType(FieldType.TIMESTAMP.name()),
          new TableFieldSchema().setName("commandName").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("clientId").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("privilegeLevel").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("eppTarget").setType(FieldType.STRING.name()),
          new TableFieldSchema().setName("eppStatus").setType(FieldType.INTEGER.name()),
          new TableFieldSchema().setName("attempts").setType(FieldType.INTEGER.name()));

  static final ImmutableList<TableFieldSchema> ENTITY_INTEGRITY_ALERTS_SCHEMA_FIELDS =
      ImmutableList.<TableFieldSchema>of(
          new TableFieldSchema()
              .setName(ENTITY_INTEGRITY_ALERTS_FIELD_SCANTIME)
              .setType(FieldType.TIMESTAMP.name()),
          new TableFieldSchema()
              .setName(ENTITY_INTEGRITY_ALERTS_FIELD_SOURCE)
              .setType(FieldType.STRING.name()),
          new TableFieldSchema()
              .setName(ENTITY_INTEGRITY_ALERTS_FIELD_TARGET)
              .setType(FieldType.STRING.name()),
          new TableFieldSchema()
              .setName(ENTITY_INTEGRITY_ALERTS_FIELD_MESSAGE)
              .setType(FieldType.STRING.name()));

  @NonFinalForTesting
  static Map<String, ImmutableList<TableFieldSchema>> knownTableSchemas =
      new ImmutableMap.Builder<String, ImmutableList<TableFieldSchema>>()
          .put(EPPMETRICS_TABLE_ID, EPPMETRICS_SCHEMA_FIELDS)
          .put(ENTITY_INTEGRITY_ALERTS_TABLE_ID, ENTITY_INTEGRITY_ALERTS_SCHEMA_FIELDS)
          .build();

  private BigquerySchemas() {}
}
