-- Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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
