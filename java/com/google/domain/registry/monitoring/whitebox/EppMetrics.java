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

import com.google.domain.registry.bigquery.BigquerySchemas;
import com.google.domain.registry.model.eppoutput.Result.Code;

/** The EPP Metrics collector. See {@link Metrics}. */
public class EppMetrics extends Metrics {

  public EppMetrics() {
    setTableId(BigquerySchemas.EPPMETRICS_TABLE_ID);
    fields.put("attempts", 0);
  }

  public void setCommandName(String name) {
    fields.put("commandName", name);
  }

  public void setClientId(String clientId) {
    fields.put("clientId", clientId);
  }

  public void setPrivilegeLevel(String level) {
    fields.put("privilegeLevel", level);
  }

  public void setEppTarget(String eppTarget) {
    fields.put("eppTarget", eppTarget);
  }

  public void setRequestId(String requestId) {
    fields.put("requestId", requestId);
  }

  public void setEppStatus(Code status) {
    fields.put("eppStatus", String.valueOf(status.code));
  }

  public void incrementAttempts() {
    int attempts = (int) fields.get("attempts");
    fields.put("attempts", attempts + 1);
  }
}
