  -- Copyright 2017 The Nomulus Authors. All Rights Reserved.
  --
  -- Licensed under the Apache License, Version 2.0 (the "License");
  -- you may not use this file except in compliance with the License.
  -- You may obtain a copy of the License at
  --
  --     http://www.apache.org/licenses/LICENSE-2.0
  --
  -- Unless required by applicable law or agreed to in writing, software
  -- distributed under the License is distributed on an "AS IS" BASIS,
  -- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -- See the License for the specific language governing permissions and
  -- limitations under the License.

  -- Query for all registrar statuses:
  -- production, ramping up (OTE), or pre-ramp-up (requested).

SELECT
  -- Applies to all TLDs, hence the 'null' magic value.
  STRING(NULL) AS tld,
  CASE WHEN access_type = 'PROD' AND registrar_name IS NOT NULL
    THEN 'operational-registrars'
  WHEN access_type = 'OTE' AND registrar_name IS NOT NULL
    THEN 'ramp-up-registrars'
  WHEN access_type IS NULL AND registrar_name IS NOT NULL
    THEN 'pre-ramp-up-registrars'
  -- The import process is imprecise; filter out invalid rows.
  ELSE 'not-applicable' END AS metricName,
  INTEGER(COUNT(registrar_id)) AS count
FROM
  [%REGISTRAR_DATA_SET%.%REGISTRAR_STATUS_TABLE%]
GROUP BY metricName
