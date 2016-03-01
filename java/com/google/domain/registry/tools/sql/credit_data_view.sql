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
