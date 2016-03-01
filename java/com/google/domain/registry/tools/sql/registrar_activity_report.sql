SELECT
  STRING(timestamp) AS date,
  command,
  resourceType,
  resource,
  trid_clientTransactionId,
  trid_serverTransactionId,
  period
FROM
  ReportingHistory
WHERE
  tld = '%TLD%'
  AND registrar = '%REGISTRAR%'
  AND timestamp >= TIMESTAMP('%STARTTIME%')
  AND timestamp < TIMESTAMP('%ENDTIME%')
ORDER BY
  timestamp
