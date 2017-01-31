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

-- Premium List Data View SQL
--
-- This query generates a table of all current premium list data that
-- is active on a TLD in RegistryData.
SELECT
  listName,
  tld,
  label,
  CONCAT(label, '.', tld) AS domain,
  price,
FROM (
  -- Join the PremiumList info with RegistryData so that we can properly map
  -- a given TLD to a given PremiumList (even though by convention the list
  -- name equals the TLD name, this is not guaranteed).  This effectively
  -- produces one PremiumList copy for each TLD where it's the active list.
  SELECT
    listName,
    tld,
    -- We use the revision that's active on the PremiumList to determine
    -- which PremiumListEntry entries constitute the current list data.
    -- Since backups are only eventually consistent, this could bite us
    -- if a PremiumList's data is ever replaced and the full replacement
    -- entry-wise doesn't make it into the backup snapshot.
    -- TODO(b/21445712): Figure out a way to avoid this issue?
    revisionKeyPath,
  FROM (
    SELECT
      __key__.name AS listName,
      __key__.path AS keyPath,
      revisionKey.path AS revisionKeyPath,
    FROM
      [%SOURCE_DATASET%.PremiumList]
    ) AS PremiumList
  JOIN (
    SELECT
      tld,
      premiumListPath,
    FROM
      [%DEST_DATASET%.RegistryData]
   ) AS RegistryData
  ON
    PremiumList.keyPath = RegistryData.premiumListPath
  ) AS PremiumList
-- Left join against the entries and pick up those parented on the active
-- revision that we got from PremiumList.
LEFT JOIN EACH (
  SELECT
    REGEXP_EXTRACT(__key__.path, '(.+), "PremiumListEntry", .*')
      AS parentKeyPath,
    __key__.name AS label,
    price,
  FROM
    [%SOURCE_DATASET%.PremiumListEntry]
  ) AS PremiumListEntry
ON
  PremiumList.revisionKeyPath = PremiumListEntry.parentKeyPath
ORDER BY
  listName,
  tld,
  label
