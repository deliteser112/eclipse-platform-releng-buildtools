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
