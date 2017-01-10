-- Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

-- Recurring Billing Event Data View SQL
--
-- This query expands Recurring billing events into individual rows for each
-- recurrence of the event that has happened up to the time the query is run.
-- Since the only Recurring events are for automatic renewals, this means each
-- row stands in for the renewal OneTime that would be created for a given
-- Recurring event if it were materialized as individual explicit renewals.
--
-- TODO(b/27562876): Drop this query once we begin materializing Recurring
-- billing events as synthetic OneTime events.
SELECT
  id,
  kind,  -- Needed for joining Cancellation information downstream.
  -- Add the appropriate autorenew grace period length for this TLD to determine
  -- the time at which this auto-renewal should be billed.
  DATE_ADD(eventTime, registry.autorenewGracePeriodSeconds, 'SECOND')
    AS billingTime,
  eventTime,
  clientId,
  recurrence.tld AS tld,
  'RENEW' AS reason,  -- These are always auto-renewals, so use RENEW.
  targetId,
  domainRepoId,
  1 AS periodYears,  -- Auto-renewals are always for 1 year.
  -- Use the premium price if one's configured in the current list, otherwise
  -- fall back to the TLD-wide standard renewal cost.
  IF(premium.price IS NOT NULL, premium.price, renewCost) AS cost,
  flags,
FROM (
  -- Nested query to compute eventTime of this recurrence so it's available to
  -- the JOIN against RegistryData.
  SELECT
    id,
    kind,
    tld,
    -- Construct the event time for this recurrence by adding years to the
    -- Recurring event's original event time.  Leap years are not an issue since
    -- we ensure that domains never expire (and hence never start to auto-renew)
    -- on Feb. 29th, so the recurrence time (as month, day, and millis of day)
    -- will always be preserved if we add whole numbers of years.
    DATE_ADD(eventTime, yearsToAdd, 'YEAR') AS eventTime,
    targetId,
    domainRepoId,
    clientId,
    reason,
    recurrenceEndTime,
    flags,
  FROM
    -- Nested flattened query that expands single Recurring rows into multiple
    -- rows for each recurrrence of that Recurring event.  It does this by
    -- computing the number of recurrences to generate, constructing a repeated
    -- field with that cardinality, and then flattening that repeated field.
    FLATTEN(
      (
      SELECT
        id,
        kind,
        tld,
        eventTime,
        targetId,
        domainRepoId,
        clientId,
        reason,
        recurrenceEndTime,
        flags,
        -- Make a repeated field with N elements (N = "numRecurrenceYears") by
        -- padding a string to length N, using the SPLIT() function with the
        -- empty string delimiter to generate a repeated field with one element
        -- per character, and then using POSITION() to convert that field into
        -- one whose values are numeric ranging from 0 to N - 1.
        -- TODO(b/20829992): replace this hack with a UDF?
        POSITION(SPLIT(RPAD('', numRecurrenceYears, '.'), '')) - 1
          AS yearsToAdd,
      FROM (
        -- Nested query to compute the number of recurrences in scope for each
        -- Recurring event, to pass to the outer row-expansion query.
        SELECT
          __key__.id AS id,
          __key__.kind AS kind,
          -- TODO(b/20828509): make this robust to multi-part TLDs.
          LAST(SPLIT(targetId, '.')) AS tld,
          eventTime,
          targetId,
          -- TODO(b/20828509): see if this can be expressed more cleanly.
          REGEXP_EXTRACT(__key__.path, '"DomainBase", "([^"]+)"') AS domainRepoId,
          clientId,
          reason,
          recurrenceEndTime,
          -- The number of recurrences we generate is one per year for every
          -- year from the event time (first recurrence) to the earlier of
          -- either the current time or the end time, inclusive.  This may
          -- generate an extra recurrence at the end (if the recurrence end
          -- time is earlier in the year than the recurrence) but we filter
          -- it out below.
          GREATEST(
            0,  -- Bound below by zero.
            LEAST(YEAR(CURRENT_TIMESTAMP()), YEAR(recurrenceEndTime))
              - YEAR(eventTime) + 1
            ) AS numRecurrenceYears,
          -- Note: there has never been a Recurring event with a flag to date,
          --   so the flags column does not exist for the Recurring table.  If
          --   we ever want to use real Recurring flags, this line would look
          --   like "GROUP_CONCAT_UNQUOTED(flags) WITHIN RECORD AS flags".
          -- Create a synthetic AUTO_RENEW flag to differentiate this record
          -- from a manual renewal.
          'AUTO_RENEW' AS flags,
        FROM
          [%SOURCE_DATASET%.Recurring]
        WHERE
          -- Filter out Registry 1.0 data - TODO(b/20828509): remove this.
          __key__.namespace = ''
        )
      WHERE
        -- Filter out prober data.
        tld IN
          (SELECT tld FROM [%DEST_DATASET%.RegistryData] WHERE type = 'REAL')
        -- Exclude recurring events that would generate zero recurrences,
        -- which are those with an event time beyond the current year.
        AND numRecurrenceYears > 0
      ),
      -- Second argument to FLATTEN(), explained above.
      yearsToAdd)
  ) AS recurrence

-- Join recurrence information by TLD to policy information for that TLD that
-- determines recurrence properties - grace period length and renewal cost.
LEFT OUTER JOIN (
  SELECT
    tld,
    -- TODO(b/20764952): If we ever want to have more than one renew billing
    -- cost, the logic here will need to pick the right cost based on the
    -- eventTime value of the recurrence.
    renewBillingCost AS renewCost,
    autorenewGracePeriodSeconds,
  FROM
    [%DEST_DATASET%.RegistryData]
  ) AS registry
ON
  recurrence.tld = registry.tld

-- Join recurrence information by SLD to premium pricing information for that
-- SLD, taken from the currently active premium list.  Note that this doesn't
-- account in any way for a premium list that changes over time; we always use
-- the current list, regardless of when the recurrence is happening.
-- TODO(b/21445712): Make this reflect the pricing at the recurrence times.
LEFT JOIN EACH (
  SELECT
    domain,
    price,
  FROM
    [%DEST_DATASET%.PremiumListData]
  ) AS premium
ON
  recurrence.targetId = premium.domain

WHERE
  -- Filter out generated recurrences where the event time of that recurrence
  -- is at or after the end time of the recurring event (this handles the case
  -- of generating an extra final recurrence, described above).
  eventTime < recurrenceEndTime
  -- Restrict this view to just recurrences that have "happened" by the time
  -- the query is executed.
  AND eventTime <= CURRENT_TIMESTAMP()
ORDER BY
  -- Show the newest recurrences first, then break ties by ID and TLD, like
  -- we do for BillingData.
  billingTime DESC,
  id,
  tld
