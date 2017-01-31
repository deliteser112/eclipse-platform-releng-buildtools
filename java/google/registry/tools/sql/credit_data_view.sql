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

-- Credit Data View SQL
--
-- This query post-processes RegistrarCredit entities and joins them to the
-- corresponding owning Registrar entities.  The join is mostly a no-op, but
-- it does ensure that if extracting the registrar parent from a credit fails
-- then this view will indicate that by showing the registrar ID as null.
-- TODO(b/19031915): add optional sanity-checking for that type of invariant.
SELECT
  Registrar.registrarId AS registrarId,
  RegistrarCredit.__key__.id AS creditId,
  RegistrarCredit.type AS type,
  CreditType.priority AS typePriority,
  creationTime,
  currency,
  description,
  tld,
FROM (
  SELECT
    *,
    REGEXP_EXTRACT(__key__.path, '"Registrar", "(.+?)"') AS registrarId
  FROM
    [%SOURCE_DATASET%.RegistrarCredit]
  ) AS RegistrarCredit
LEFT JOIN
  (SELECT registrarId FROM [%DEST_DATASET%.RegistrarData]) AS Registrar
ON
  RegistrarCredit.registrarId = Registrar.registrarId
LEFT JOIN (
  -- TODO(b/19031546): Generate this table from the CreditType enum.
  SELECT * FROM
    (SELECT 'AUCTION' AS type, 1 AS priority),
    (SELECT 'PROMOTION' AS type, 2 AS priority),
  ) AS CreditType
ON
  RegistrarCredit.type = CreditType.type
ORDER BY
  creationTime,
  creditId
