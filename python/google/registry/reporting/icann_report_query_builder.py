# Copyright 2017 The Nomulus Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""ICANN reporting BigQuery query construction logic.

The IcannReportQueryBuilder class contains logic for constructing the
multi-part BigQuery queries used to produce ICANN monthly reports.  These
queries are fairly complicated; see the design doc published to
nomulus-discuss@googlegroups.com for an overview.

Currently, this class only supports building the query for activity
reports (not transaction reports).
"""
import datetime

# This signature must match the one logged by FlowReporter - see
# cs/symbol:google.registry.flows.FlowReporter.METADATA_LOG_SIGNATURE
FLOWREPORTER_LOG_SIGNATURE = 'FLOW-LOG-SIGNATURE-METADATA'


class IcannReportQueryBuilder(object):
  """Container for methods to build BigQuery queries for ICANN reporting."""

  def BuildActivityReportQuery(self, month, registrar_count):
    """Returns the assembled activity report query for a given month.

    Specifically, we instantiate the outermost activity report query by pointing
    it at the union of a series of "data source" queries that each produce data
    used to generate certain metrics.  These queries in turn rely on some common
    lower-level data source queries (monthly logs, both raw and EPP-parsed).

    Args:
      month: (str) month of the report to generate, in YYYY-MM format
      registrar_count: (int) total number of registrars in the registry system

    Returns:
      (str) the fully-instantiated activity report query SQL
    """
    # Construct some date-related parameters from the given month.
    this_month_date = datetime.datetime.strptime(month, '%Y-%m').date()
    # Hacky way to compute the start of the next month - add enough days to get
    # to the next month (e.g. 31), then set the day to 1.  It'd be cleaner to
    # use dateutils.relativedelta(months=1) but the dependency is a pain.
    month_delta = datetime.timedelta(days=31)
    next_month_date = (this_month_date + month_delta).replace(day=1)
    this_yearmonth = this_month_date.strftime('%Y-%m')
    next_yearmonth = next_month_date.strftime('%Y-%m')

    # Construct the queries themselves.
    logs_query = self._MakeMonthlyLogsQuery(this_yearmonth, next_yearmonth)
    data_source_queries = [
        self._MakeActivityOperationalRegistrarsQuery(next_yearmonth),
        self._MakeActivityAllRampedUpRegistrarsQuery(next_yearmonth),
        self._MakeActivityAllRegistrarsQuery(registrar_count),
        self._MakeActivityWhoisQuery(logs_query),
        self._MakeActivityDnsQuery(),
        self._MakeActivityEppSrsMetricsQuery(logs_query)
    ]
    return _StripTrailingWhitespaceFromLines(self._MakeActivityReportQuery(
        data_source_queries))

  def _MakeMonthlyLogsQuery(self, this_yearmonth, next_yearmonth):
    # TODO(b/20725722): add a real docstring.
    # pylint: disable=missing-docstring
    query = r"""
      -- Query AppEngine request logs for the report month.
      SELECT
        protoPayload.resource AS requestPath,
        protoPayload.line.logMessage AS logMessage,
      FROM
        TABLE_DATE_RANGE_STRICT(
          [appengine_logs.appengine_googleapis_com_request_log_],
          TIMESTAMP('%(this_yearmonth)s-01'),
          -- End timestamp is inclusive, so subtract 1 second from the
          -- timestamp representing the start of the next month.
          DATE_ADD(TIMESTAMP('%(next_yearmonth)s-01'), -1, 'SECOND'))
    """
    return query % {'this_yearmonth': this_yearmonth,
                    'next_yearmonth': next_yearmonth}

  def _MakeActivityReportQuery(self, data_source_queries):
    """Make the overall activity report query.

    Args:
      data_source_queries: list of BigQuery SQL strings to use
        as source 'tables' for the main query; each of these
        queries must output a schema as follows:

        STRING tld / STRING metricName / INTEGER count

        A null TLD indicates that the metric counts towards
        all TLDs.

    Returns:
      query as a string of BigQuery SQL
    """
    query = r"""
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
          %(joined_data_sources)s
          -- END JOINED DATA SOURCES --
      ) AS TldMetrics
      WHERE Tld.tld = TldMetrics.tld OR TldMetrics.tld IS NULL
      GROUP BY tld
      ORDER BY tld
    """
    # Turn each data source query into a subquery in parentheses, and join
    # them together with comments (representing a table union).
    joined_data_sources = '\n' + ',\n'.join(
        '(\n%s\n)' % query for query in data_source_queries)
    return query % {'joined_data_sources': joined_data_sources}

  def _MakeActivityOperationalRegistrarsQuery(self, next_yearmonth):
    # TODO(b/20725722): add a real docstring.
    # pylint: disable=missing-docstring
    query = r"""
      -- Query for operational-registrars metric.
      SELECT
        allowedTlds AS tld,
        'operational-registrars' AS metricName,
        INTEGER(COUNT(__key__.name)) AS count,
      FROM [domain-registry:latest_snapshot.Registrar]
      WHERE type = 'REAL'
        AND creationTime < TIMESTAMP('%(next_yearmonth)s-01')
      GROUP BY tld
    """
    return query % {'next_yearmonth': next_yearmonth}

  def _MakeActivityAllRampedUpRegistrarsQuery(self, next_yearmonth):
    # TODO(b/20725722): add a real docstring.
    # pylint: disable=missing-docstring
    query = r"""
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
        AND creationTime < TIMESTAMP('%(next_yearmonth)s-01')
    """
    return query % {'next_yearmonth': next_yearmonth}

  def _MakeActivityAllRegistrarsQuery(self, registrar_count):
    # TODO(b/20725722): add a real docstring.
    # pylint: disable=missing-docstring
    query = """
      -- Query for all-registrars metric.
      SELECT
        STRING(NULL) AS tld,  -- Applies to all TLDs.
        'all-registrars' AS metricName,
        INTEGER('%(registrar_count)s') AS count,
    """
    return query % {'registrar_count': registrar_count}

  def _MakeActivityWhoisQuery(self, logs_query):
    # TODO(b/20725722): add a real docstring.
    # pylint: disable=missing-docstring
    query = r"""
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
        %(logs_query)s
        -- END LOGS QUERY --
      )
      GROUP BY metricName
      HAVING metricName IS NOT NULL
    """
    return query % {'logs_query': logs_query}

  def _MakeActivityDnsQuery(self):
    # TODO(b/20725722): add a real docstring.
    # pylint: disable=missing-docstring
    query = r"""
      -- Query for DNS metrics.
      SELECT
        STRING(NULL) AS tld,
        metricName,
        -1 AS count,
      FROM
        (SELECT 'dns-udp-queries' AS metricName),
        (SELECT 'dns-tcp-queries' AS metricName)
    """
    return query

  def _MakeActivityEppSrsMetricsQuery(self, logs_query):
    # TODO(b/20725722): add a real docstring.
    # pylint: disable=missing-docstring
    query = r"""
      -- Query FlowReporter JSON log messages and calculate SRS metrics.
      SELECT
        tld,
        activityReportField AS metricName,
        -- Manual INTEGER cast to work around a BigQuery bug (b/14560012).
        INTEGER(COUNT(*)) AS count,
      FROM
        -- Flatten the "tld" column (repeated) so that domain checks for names
        -- across multiple TLDs are counted towards each checked TLD as though
        -- there were one copy of this row per TLD (the effect of flattening).
        FLATTEN((
          SELECT
            -- Use some ugly regex hackery to convert JSON list of strings into
            -- repeated string values, since there's no built-in for this.
            -- TODO(b/20829992): replace with "JSON.parse()" inside a JS UDF
            --   once we can use GoogleSQL; example in b/37629674#comment2.
            REGEXP_EXTRACT(
              SPLIT(
                REGEXP_EXTRACT(
                  JSON_EXTRACT(json, '$.tlds'),
                  r'^\[(.*)\]$')),
              '^"(.*)"$') AS tld,
            -- TODO(b/XXX): remove rawTlds after June 2017 (see below).
            JSON_EXTRACT_SCALAR(json, '$.resourceType') AS resourceType,
            JSON_EXTRACT_SCALAR(json, '$.icannActivityReportField')
              AS activityReportField,
          FROM (
            SELECT
              -- Extract JSON payload following log signature.
              REGEXP_EXTRACT(logMessage, r'%(log_signature)s: (.*)\n?$')
                AS json,
            FROM (
              -- BEGIN LOGS QUERY --
              %(logs_query)s
              -- END LOGS QUERY --
            )
            WHERE logMessage CONTAINS '%(log_signature)s'
          )
        ),
        -- Second argument to flatten (see above).
        tld)
      -- Exclude cases that can't be tabulated correctly - activity report field
      -- is null/empty, or the TLD is null/empty even though it's a domain flow.
      WHERE
        activityReportField != '' AND (tld != '' OR resourceType != 'domain')
      GROUP BY tld, metricName
      ORDER BY tld, metricName
    """
    return query % {'logs_query': logs_query,
                    'log_signature': FLOWREPORTER_LOG_SIGNATURE}


def _StripTrailingWhitespaceFromLines(string):
  """Strips trailing whitespace from each line of the provided string.

  Args:
    string: (str) string to remove trailing whitespace from

  Returns:
    (str) input string, with trailing whitespace stripped from each line
  """
  return '\n'.join(line.rstrip() for line in string.split('\n'))
