SELECT
  modificationTime AS timestamp,
  HistoryEntry.namespace AS tld,
  clientId AS registrar,
  type AS command,
  CASE WHEN ReportingIdentifiers.kind = 'DomainBase' THEN 'DOMAIN'
       WHEN ReportingIdentifiers.kind = 'HostResource' THEN 'HOST'
       WHEN ReportingIdentifiers.kind = 'ContactResource' THEN 'CONTACT'
       END AS resourceType,
  ReportingIdentifiers.value AS resource,
  trid.clientTransactionId,
  trid.serverTransactionId,
  period
FROM (
  SELECT
    type,
    clientId,
    modificationTime,
    trid.clientTransactionId,
    trid.serverTransactionId,
    CASE WHEN period.value IS NOT NULL
         THEN CONCAT(STRING(period.value), ' ', LOWER(period.unit))
         END AS period,
    __key__.namespace AS namespace,
    REGEXP_EXTRACT(__key__.path, r'per-tld", "([^"]+)"') AS kind,
    INTEGER(REGEXP_EXTRACT(__key__.path, r'per-tld", "[^"]+", (\d+)')) AS id
  FROM
    HistoryEntry
  WHERE
    clientId <> 'prober'
    AND __key__.namespace <> 'test'
    AND NOT __key__.namespace CONTAINS '.test'
    AND NOT bySuperuser) AS HistoryEntry
JOIN
  ReportingIdentifiers
  ON  ReportingIdentifiers.namespace = HistoryEntry.namespace
  AND ReportingIdentifiers.kind = HistoryEntry.kind
  AND ReportingIdentifiers.id = HistoryEntry.id
