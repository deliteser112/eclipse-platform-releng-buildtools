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

-- Registrar Data View SQL
--
-- This query lists each registrar ID with type, billing ID, and allowed TLDs.
--
-- Note that there is one row per registrar (allowedTlds is repeated), versus
-- registrar_account_data_view.sql which has multiple rows per registrar.
SELECT
  __key__.name AS registrarId,
  type,
  billingIdentifier AS billingId,
  allowedTlds
FROM
  [%SOURCE_DATASET%.Registrar]
-- Note: We can't ORDER BY registrarId here because ORDER/GROUP BY will
--   flatten results, and the allowedTlds field above is repeated.
-- TODO(b/19031339): Add "ORDER BY" if the BigQuery known issue is fixed.
