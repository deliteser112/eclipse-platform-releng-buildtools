-- Currency Table Creation SQL
--
-- This query generates a static table of currency information.
SELECT
  currency, conversionToUsd, exponent
FROM
  (SELECT 'JPY' AS currency, 0.0098 AS conversionToUsd, 0 AS exponent),
  (SELECT 'USD' AS currency, 0.0100 AS conversionToUsd, 2 AS exponent)
