-- Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

-- Credit Balance Data View SQL
--
-- This query post-processes RegistrarCreditBalance entities to collapse them
-- down to one 'true' amount per credit ID and effective time, eliminating any
-- duplicates by choosing the most recently written balance entry of the set,
-- and then joins these 'real' balances to the CreditData view.
--
-- The result is a list showing how each credit's balance has developed over
-- time, which can be used to find the actual balance of a given credit at
-- any particular point in time (e.g. the time when invoices are run).
SELECT
  CreditData.registrarId AS registrarId,
  CreditData.creditId AS creditId,
  effectiveTime,
  REGEXP_EXTRACT(amount, '(.+) ') AS currency,
  INTEGER(REGEXP_REPLACE(amount, r'\D+', '')) AS amountMinor
FROM (
  SELECT
    creditId,
    effectiveTime,
    amount,
    ROW_NUMBER() OVER (
      PARTITION BY
        creditId,
        effectiveTime
      ORDER BY
        writtenTime DESC
      ) AS recencyRank
  FROM (
    SELECT
      INTEGER(REGEXP_EXTRACT(__key__.path, '"RegistrarCredit", (.+?),'))
        AS creditId,
      effectiveTime,
      writtenTime,
      amount
    FROM [%SOURCE_DATASET%.RegistrarCreditBalance]
    )
  ) AS BalanceData
LEFT JOIN EACH
  [%DEST_DATASET%.CreditData] AS CreditData
ON
  BalanceData.creditId = CreditData.creditId
WHERE
  recencyRank = 1
ORDER BY
  creditId,
  effectiveTime
