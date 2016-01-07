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

-- Registry Data View SQL
--
-- This query lists registry fields necessary for billing.
--
-- TODO(b/20764952): extend this view to support timed transition properties.
-- TODO(b/18092292): add a column for whether the TLD is "billable" or not.
SELECT
  tldStr AS tld,
  tldType AS type,
  -- This relies on the fact that we currently haven't ever used more than one
  -- value with renewBillingCostTransitions.
  -- TODO(b/20764952): fix this limitation.
  FIRST(renewBillingCostTransitions.billingCost) WITHIN RECORD
    AS renewBillingCost,
  -- Grace period lengths are stored in the ISO 8601 duration format as a
  -- duration in seconds, which produces a string of the form "PT####S".
  INTEGER(REGEXP_EXTRACT(autoRenewGracePeriodLength, r'PT(.+)S'))
    AS autorenewGracePeriodSeconds,
  premiumList.path AS premiumListPath,
  -- TODO(b/18265521): remove this column once the referenced bug is fixed;
  -- this column is only included because without it BigQuery will complain
  -- that we aren't consuming reservedLists (due to it having a repeated .path
  -- child field, which overlaps with premiumList.path above).
  GROUP_CONCAT_UNQUOTED(reservedLists.path, "/") WITHIN RECORD
    AS reservedListPaths,
FROM
  [%SOURCE_DATASET%.Registry]
WHERE
   -- Filter out Registry 1.0 data - TODO(b/20828509): remove this.
  __key__.namespace = ''
ORDER BY
  tld
