-- Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

-- Registrar Data View SQL
--
-- This query lists registrar IDs with billing IDs and allowed TLDs.
SELECT
  __key__.name AS registrarId,
  billingIdentifier AS billingId,
  allowedTlds
FROM
  [%SOURCE_DATASET%.Registrar]
-- TODO(b/19031620): Add filter to just include registrars with type=REAL.
-- Note: We can't ORDER BY registrarId here because ORDER/GROUP BY will
--   flatten results, and the allowedTlds field above is repeated.
-- TODO(b/19031339): Add "ORDER BY" if the BigQuery known issue is fixed.
