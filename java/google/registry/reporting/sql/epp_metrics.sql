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

  -- Query FlowReporter JSON log messages and calculate SRS metrics.

  -- We use regex's over the monthly appengine logs to determine how many
  -- EPP requests we received for each command.

SELECT
  tld,
  activityReportField AS metricName,
  -- Manual INTEGER cast to work around a BigQuery bug (b/14560012).
  INTEGER(COUNT(*)) AS count,
FROM
  -- Flatten the "tld" column (repeated) so that domain checks for names
  -- across multiple TLDs are counted towards each checked TLD as though
  -- there were one copy of this row per TLD (the effect of flattening).
  FLATTEN((
    SELECT
      -- Use some ugly regex hackery to convert JSON list of strings into
      -- repeated string values, since there's no built-in for this.
      -- TODO(b/20829992): replace with "JSON.parse()" inside a JS UDF
      --   once we can use GoogleSQL; example in b/37629674#comment2.
      -- e.g. JSON:"{"commandType":"check"...,"targetIds":["ais.a.how"],
      -- "tld":"","tlds":["a.how"],"icannActivityReportField":"srs-dom-check"}
      REGEXP_EXTRACT(
        SPLIT(
          REGEXP_EXTRACT(
            JSON_EXTRACT(json, '$.tlds'),
            r'^\[(.*)\]$')),
        '^"(.*)"$') AS tld,
      -- TODO(b/XXX): remove rawTlds after June 2017 (see below).
      JSON_EXTRACT_SCALAR(json, '$.resourceType') AS resourceType,
      JSON_EXTRACT_SCALAR(json, '$.icannActivityReportField')
        AS activityReportField,
    FROM (
      SELECT
        -- Extract JSON payload following log signature.
        REGEXP_EXTRACT(logMessage, r'FLOW-LOG-SIGNATURE-METADATA: (.*)\n?$')
          AS json,
      FROM
        [%MONTHLY_LOGS_DATA_SET%.%MONTHLY_LOGS_TABLE%]
      WHERE logMessage CONTAINS 'FLOW-LOG-SIGNATURE-METADATA'
    )
  ),
  -- Second argument to flatten (see above).
  tld)
-- Exclude cases that can't be tabulated correctly - activity report field
-- is null/empty, or the TLD is null/empty even though it's a domain flow.
WHERE
  activityReportField != '' AND (tld != '' OR resourceType != 'domain')
GROUP BY tld, metricName
ORDER BY tld, metricName
