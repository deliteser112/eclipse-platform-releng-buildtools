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

  public static final String EPPMETRICS_TABLE_ID = "eppMetrics";

  @NonFinalForTesting
  static Map<String, ImmutableList<TableFieldSchema>> knownTableSchemas =
      ImmutableMap.of(EPPMETRICS_TABLE_ID, EPPMETRICS_SCHEMA_FIELDS);

  private BigquerySchemas() {}
}
