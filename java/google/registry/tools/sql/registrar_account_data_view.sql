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

-- Registrar Account Data View SQL
--
-- This query lists registrar IDs with billing account IDs and corresponding
-- currencies.
--
-- The table that contains both billing account IDs and currencies as repeated
-- fields are first flattened to two separate tables, with IDs and currencies
-- in each table, and the corresponding row numbers, partitioned over registrar.
-- The row numbers are used to join the two tables together, restoring the
-- original mapping between IDs and currencies.
SELECT
  I.registrarId AS registrarId,
  -- Apply no-op STRING() function to keep BigQuery schema transformation logic
  -- from wanting different names in direct query vs save-as-view.
  STRING(C.billingAccountMap.currency) AS currency,
  STRING(I.billingAccountMap.accountId) AS billingAccountId,
FROM (
  SELECT
    __key__.name AS registrarId,
    billingAccountMap.accountId,
    ROW_NUMBER() OVER (PARTITION BY registrarId) AS pos
  FROM
    FLATTEN([latest_snapshot.Registrar], billingAccountMap.accountId)) AS I
JOIN (
  SELECT
    __key__.name AS registrarId,
    billingAccountMap.currency,
    ROW_NUMBER() OVER (PARTITION BY registrarId) AS pos
  FROM
    FLATTEN([%SOURCE_DATASET%.Registrar], billingAccountMap.currency)) AS C
ON
  I.registrarId == C.registrarId
  AND I.pos == C.pos
ORDER BY
  registrarId,
  I.pos
