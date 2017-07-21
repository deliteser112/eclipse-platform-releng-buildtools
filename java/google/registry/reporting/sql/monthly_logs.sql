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

  -- Query to fetch AppEngine request logs for the report month.

  -- START_OF_MONTH and END_OF_MONTH should be in YYYY-MM-01 format.

SELECT
  protoPayload.resource AS requestPath,
  protoPayload.line.logMessage AS logMessage,
FROM
  TABLE_DATE_RANGE_STRICT(
    [%APPENGINE_LOGS_DATA_SET%.%REQUEST_TABLE%],
    TIMESTAMP('%START_OF_MONTH%'),
    -- End timestamp is inclusive, so subtract 1 day from the
    -- timestamp representing the start of the next month.
    DATE_ADD(TIMESTAMP('%END_OF_MONTH%'), -1, 'DAY'))
