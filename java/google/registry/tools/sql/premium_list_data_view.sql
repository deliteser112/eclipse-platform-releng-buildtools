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
