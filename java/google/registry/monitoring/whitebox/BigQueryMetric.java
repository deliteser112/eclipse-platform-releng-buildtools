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

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A metric which can be encoded into a BigQuery row.
 *
 * @see BigQueryMetricsEnqueuer
 */
public interface BigQueryMetric {

  /** Get the BigQuery table name for this metric. */
  String getTableId();

  /** Get the schema description for the BigQuery table. */
  ImmutableList<TableFieldSchema> getSchemaFields();

  /** Get a map of the row values for this metric instance. */
  ImmutableMap<String, String> getBigQueryRowEncoding();
}
