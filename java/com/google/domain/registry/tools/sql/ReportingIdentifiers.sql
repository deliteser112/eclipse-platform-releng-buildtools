SELECT
  namespace,
  kind,
  id,
  value
FROM
  (SELECT
     __key__.namespace as namespace,
     __key__.kind as kind,
     __key__.id as id,
     fullyQualifiedDomainName AS value
   FROM
     DomainBase),
  (SELECT
     __key__.namespace as namespace,
     __key__.kind as kind,
     __key__.id as id,
     fullyQualifiedHostName AS value
   FROM
     HostResource),
  (SELECT
     __key__.namespace as namespace,
     __key__.kind as kind,
     __key__.id as id,
     contactId AS value
   FROM
     ContactResource)
WHERE
  namespace <> 'test'
  AND NOT namespace CONTAINS '.test'
