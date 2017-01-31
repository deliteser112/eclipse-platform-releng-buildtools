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

      SELECT
        Tld.tld AS tld,
        SUM(IF(metricName = 'operational-registrars', count, 0)) AS operational_registrars,
        -- Compute ramp-up-registrars as all-ramped-up-registrars
        -- minus operational-registrars, with a floor of 0.
        GREATEST(0, SUM(
          CASE
            WHEN metricName = 'operational-registrars' THEN -count
            WHEN metricName = 'all-ramped-up-registrars' THEN count
            ELSE 0
          END)) AS ramp_up_registrars,
        -- Compute pre-ramp-up-registrars as all-registrars minus
        -- all-ramp-up-registrars, with a floor of 0.
        GREATEST(0, SUM(
          CASE
            WHEN metricName = 'all-ramped-up-registrars' THEN -count
            WHEN metricName = 'all-registrars' THEN count
            ELSE 0
          END)) AS pre_ramp_up_registrars,
        -- We don't support ZFA over SFTP, only AXFR.
        0 AS zfa_passwords,
        SUM(IF(metricName = 'whois-43-queries', count, 0))  AS whois_43_queries,
        SUM(IF(metricName = 'web-whois-queries', count, 0)) AS web_whois_queries,
        -- We don't support searchable WHOIS.
        0 AS searchable_whois_queries,
        -- DNS queries for UDP/TCP are all assumed to be recevied/responded.
        SUM(IF(metricName = 'dns-udp-queries', count, 0)) AS dns_udp_queries_received,
        SUM(IF(metricName = 'dns-udp-queries', count, 0)) AS dns_udp_queries_responded,
        SUM(IF(metricName = 'dns-tcp-queries', count, 0)) AS dns_tcp_queries_received,
        SUM(IF(metricName = 'dns-tcp-queries', count, 0)) AS dns_tcp_queries_responded,
        -- SRS metrics.
        SUM(IF(metricName = 'srs-dom-check', count, 0)) AS srs_dom_check,
        SUM(IF(metricName = 'srs-dom-create', count, 0)) AS srs_dom_create,
        SUM(IF(metricName = 'srs-dom-delete', count, 0)) AS srs_dom_delete,
        SUM(IF(metricName = 'srs-dom-info', count, 0)) AS srs_dom_info,
        SUM(IF(metricName = 'srs-dom-renew', count, 0)) AS srs_dom_renew,
        SUM(IF(metricName = 'srs-dom-rgp-restore-report', count, 0)) AS srs_dom_rgp_restore_report,
        SUM(IF(metricName = 'srs-dom-rgp-restore-request', count, 0)) AS srs_dom_rgp_restore_request,
        SUM(IF(metricName = 'srs-dom-transfer-approve', count, 0)) AS srs_dom_transfer_approve,
        SUM(IF(metricName = 'srs-dom-transfer-cancel', count, 0)) AS srs_dom_transfer_cancel,
        SUM(IF(metricName = 'srs-dom-transfer-query', count, 0)) AS srs_dom_transfer_query,
        SUM(IF(metricName = 'srs-dom-transfer-reject', count, 0)) AS srs_dom_transfer_reject,
        SUM(IF(metricName = 'srs-dom-transfer-request', count, 0)) AS srs_dom_transfer_request,
        SUM(IF(metricName = 'srs-dom-update', count, 0)) AS srs_dom_update,
        SUM(IF(metricName = 'srs-host-check', count, 0)) AS srs_host_check,
        SUM(IF(metricName = 'srs-host-create', count, 0)) AS srs_host_create,
        SUM(IF(metricName = 'srs-host-delete', count, 0)) AS srs_host_delete,
        SUM(IF(metricName = 'srs-host-info', count, 0)) AS srs_host_info,
        SUM(IF(metricName = 'srs-host-update', count, 0)) AS srs_host_update,
        SUM(IF(metricName = 'srs-cont-check', count, 0)) AS srs_cont_check,
        SUM(IF(metricName = 'srs-cont-create', count, 0)) AS srs_cont_create,
        SUM(IF(metricName = 'srs-cont-delete', count, 0)) AS srs_cont_delete,
        SUM(IF(metricName = 'srs-cont-info', count, 0)) AS srs_cont_info,
        SUM(IF(metricName = 'srs-cont-transfer-approve', count, 0)) AS srs_cont_transfer_approve,
        SUM(IF(metricName = 'srs-cont-transfer-cancel', count, 0)) AS srs_cont_transfer_cancel,
        SUM(IF(metricName = 'srs-cont-transfer-query', count, 0)) AS srs_cont_transfer_query,
        SUM(IF(metricName = 'srs-cont-transfer-reject', count, 0)) AS srs_cont_transfer_reject,
        SUM(IF(metricName = 'srs-cont-transfer-request', count, 0)) AS srs_cont_transfer_request,
        SUM(IF(metricName = 'srs-cont-update', count, 0)) AS srs_cont_update,
      -- Cross join a list of all TLDs against TLD-specific metrics and then
      -- filter so that only metrics with that TLD or a NULL TLD are counted
      -- towards a given TLD.
      FROM (
        SELECT tldStr AS tld
        FROM [latest_snapshot.Registry]
        -- Include all real TLDs that are not in pre-delegation testing.
        WHERE tldType = 'REAL'
        OMIT RECORD IF SOME(tldStateTransitions.tldState = 'PDT')
      ) AS Tld
      CROSS JOIN (
        SELECT
          tld, metricName, count
        FROM
          -- Dummy data source that ensures that all TLDs appear in report,
          -- since they'll all have at least 1 joined row that survives.
          (SELECT STRING(NULL) AS tld, STRING(NULL) AS metricName, 0 AS count),
          -- BEGIN JOINED DATA SOURCES --

(

      -- Query for operational-registrars metric.
      SELECT
        allowedTlds AS tld,
        'operational-registrars' AS metricName,
        INTEGER(COUNT(__key__.name)) AS count,
      FROM [domain-registry:latest_snapshot.Registrar]
      WHERE type = 'REAL'
        AND creationTime < TIMESTAMP('2016-07-01')
      GROUP BY tld

),
(

      -- Query for all-ramped-up-registrars metric.
      SELECT
        STRING(NULL) AS tld,  -- Applies to all TLDs.
        'all-ramped-up-registrars' AS metricName,
        -- Sandbox OT&E registrar names can have either '-{1,2,3,4}' or '{,2,3}'
        -- as suffixes - strip all of these off to get the "real" name.
        INTEGER(EXACT_COUNT_DISTINCT(
          REGEXP_EXTRACT(__key__.name, r'(.+?)(?:-?\d)?$'))) AS count,
      FROM [domain-registry-sandbox:latest_snapshot.Registrar]
      WHERE type = 'OTE'
        AND creationTime < TIMESTAMP('2016-07-01')

),
(

      -- Query for all-registrars metric.
      SELECT
        STRING(NULL) AS tld,  -- Applies to all TLDs.
        'all-registrars' AS metricName,
        INTEGER('None') AS count,

),
(

      -- Query for WHOIS metrics.
      SELECT
        STRING(NULL) AS tld,  -- Applies to all TLDs.
        -- Whois queries over port 43 get forwarded by the proxy to /_dr/whois,
        -- while web queries come in via /whois/<params>.
        CASE WHEN requestPath = '/_dr/whois' THEN 'whois-43-queries'
             WHEN LEFT(requestPath, 7) = '/whois/' THEN 'web-whois-queries'
             END AS metricName,
        INTEGER(COUNT(requestPath)) AS count,
      FROM (
        -- BEGIN LOGS QUERY --

      -- Query AppEngine request logs for the report month.
      SELECT
        protoPayload.resource AS requestPath,
        protoPayload.line.logMessage AS logMessage,
      FROM
        TABLE_DATE_RANGE_STRICT(
          [appengine_logs.appengine_googleapis_com_request_log_],
          TIMESTAMP('2016-06-01'),
          -- End timestamp is inclusive, so subtract 1 second from the
          -- timestamp representing the start of the next month.
          DATE_ADD(TIMESTAMP('2016-07-01'), -1, 'SECOND'))

        -- END LOGS QUERY --
       )
      GROUP BY metricName
      HAVING metricName IS NOT NULL

),
(

      -- Query for DNS metrics.
      SELECT
        STRING(NULL) AS tld,
        metricName,
        -1 AS count,
      FROM
        (SELECT 'dns-udp-queries' AS metricName),
        (SELECT 'dns-tcp-queries' AS metricName)

),
(

      -- Query EPP XML messages and calculate SRS metrics.
      SELECT
        domainTld AS tld,
        -- SRS metric names follow a set pattern corresponding to the EPP
        -- protocol elements.  First we extract the 'inner' command element in
        -- EPP, e.g. <domain:create>, which is the resource type followed by
        -- the standard EPP command type.  To get the metric name, we add the
        -- prefix 'srs-', abbreviate 'domain' as 'dom' and 'contact' as 'cont',
        -- and replace ':' with '-' to produce 'srs-dom-create'.
        --
        -- Transfers have subcommands indicated by an 'op' attribute, which we
        -- extract and add as an extra suffix for transfer commands, so e.g.
        -- 'srs-cont-transfer-approve'.  Domain restores are domain updates
        -- with a special <rgp:restore> element; if present, the command counts
        -- under the srs-dom-rgp-restore-{request,report} metric (depending on
        -- the value of the 'op' attribute) instead of srs-dom-update.
        CONCAT(
          'srs-',
          REPLACE(REPLACE(REPLACE(
            CASE
              WHEN NOT restoreOp IS NULL THEN CONCAT('domain-rgp-restore-', restoreOp)
              WHEN commandType = 'transfer' THEN CONCAT(innerCommand, '-', commandOpArg)
              ELSE innerCommand
            END,
          ':', '-'), 'domain', 'dom'), 'contact', 'cont')
        ) AS metricName,
        INTEGER(COUNT(xml)) AS count,
      FROM (
      SELECT
        -- Extract salient bits of the EPP XML using regexes.  This is fairly
        -- safe since the EPP gets schema-validated and pretty-printed before
        -- getting logged, and so it looks something like this:
        --
        --   <command>
        --     <transfer op="request">
        --       <domain:transfer ...
        --
        -- From that, we parse out 'transfer' as the command type from the name
        -- of the first element after <command>, 'request' as the value of the
        -- 'op' attribute of that element (if any), and 'domain:transfer' as
        -- the inner command from the name of the subsequent element.
        --
        -- Domain commands all have at least one <domain:name> element (more
        -- than one for domain checks, but we just count the first), from which
        -- we extract the domain TLD as everything after the first dot in the
        -- element value.  This won't work if the client mistakenly sends a
        -- hostname (e.g. 'www.foo.example') as the domain name, but we prefer
        -- this over taking everything after the last dot so that multipart
        -- TLDs like 'co.uk' can be supported.
        --
        -- Domain restores are indicated by an <rgp:restore> element, from
        -- which we extract the value of the 'op' attribute.
        --
        -- TODO(b/20725722): preprocess the XML in FlowRunner so we don't need
        -- regex parsing of XML here (http://stackoverflow.com/a/1732454).
        --
        REGEXP_EXTRACT(xml, '(?s)<command>.*?<([a-z]+)') AS commandType,
        REGEXP_EXTRACT(xml, '(?s)<command>.*?<[a-z]+ op="(.+?)"') AS commandOpArg,
        REGEXP_EXTRACT(xml, '(?s)<command>.*?<.+?>.*?<([a-z]+:[a-z]+)') AS innerCommand,
        REGEXP_EXTRACT(xml, '<domain:name.*?>[^.]+[.](.+)</domain:name>') AS domainTld,
        REGEXP_EXTRACT(xml, '<rgp:restore op="(.+?)"/>') AS restoreOp,
        xml,
      FROM (
        -- BEGIN EPP XML LOGS QUERY --

      -- Query EPP request logs and extract the clientId and raw EPP XML.
      SELECT
        REGEXP_EXTRACT(logMessage, r'^(?:google.registry.flows.FlowRunner run|com.google.domain.registry.flows.FlowRunner run|com.google.domain.registry.util.FormattingLogger log|google.registry.util.FormattingLogger log): EPP Command\n\t.+\n\t(.+)\n') AS clientId,
        REGEXP_EXTRACT(logMessage, r'^(?:google.registry.flows.FlowRunner run|com.google.domain.registry.flows.FlowRunner run|com.google.domain.registry.util.FormattingLogger log|google.registry.util.FormattingLogger log): EPP Command\n\t.+\n\t.+\n\t.+\n\t((?s).+)$') AS xml,
      FROM (
        -- BEGIN LOGS QUERY --

      -- Query AppEngine request logs for the report month.
      SELECT
        protoPayload.resource AS requestPath,
        protoPayload.line.logMessage AS logMessage,
      FROM
        TABLE_DATE_RANGE_STRICT(
          [appengine_logs.appengine_googleapis_com_request_log_],
          TIMESTAMP('2016-06-01'),
          -- End timestamp is inclusive, so subtract 1 second from the
          -- timestamp representing the start of the next month.
          DATE_ADD(TIMESTAMP('2016-07-01'), -1, 'SECOND'))

        -- END LOGS QUERY --
       )
      WHERE
        -- EPP endpoints from the proxy, regtool, and console respectively.
        requestPath IN ('/_dr/epp', '/_dr/epptool', '/registrar-xhr')
        AND REGEXP_MATCH(logMessage, r'^(?:google.registry.flows.FlowRunner run|com.google.domain.registry.flows.FlowRunner run|com.google.domain.registry.util.FormattingLogger log|google.registry.util.FormattingLogger log): EPP Command')

        -- END EPP XML LOGS QUERY --
       )
      -- Filter to just XML that contains a <command> element (no <hello>s).
      WHERE xml CONTAINS '<command>'
      )
      -- Whitelist of EPP command types that we care about for metrics;
      -- excludes login, logout, and poll.
      WHERE commandType IN ('check', 'create', 'delete', 'info', 'renew', 'transfer', 'update')
      GROUP BY tld, metricName

)
          -- END JOINED DATA SOURCES --
      ) AS TldMetrics
      WHERE Tld.tld = TldMetrics.tld OR TldMetrics.tld IS NULL
      GROUP BY tld
      ORDER BY tld
