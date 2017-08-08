#standardSQL
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

  -- Query for DNS metrics.

  -- This is a no-op until after we transition to Google Cloud DNS, which
  -- will likely export metrics via Stackdriver.

SELECT
  -- DNS metrics apply to all tlds, which requires the 'null' magic value.
  STRING(NULL) AS tld,
  metricName,
  -- TODO(b/63388735): Change this to actually query Google Cloud DNS when ready.
  -1 AS count
FROM ((
  SELECT 'dns-udp-queries' AS metricName)
  UNION ALL
  (SELECT 'dns-tcp-queries' AS metricName))
