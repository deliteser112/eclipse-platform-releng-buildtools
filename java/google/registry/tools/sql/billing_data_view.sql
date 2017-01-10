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

-- Billing Data View SQL
--
-- This query post-processes the OneTime billing events, combines them with
-- synthetic OneTime events generated from Recurring events, and then annotates
-- the resulting data with additional information from the Registrar,
-- DomainBase, Currency, and Cancellation tables.
SELECT
  id,
  BillingEvent.billingTime AS billingTime,
  BillingEvent.eventTime AS eventTime,
  BillingEvent.clientId AS registrarId,
  Registrar.billingIdentifier AS billingId,
  BillingEvent.tld AS tld,
  IF(
    CONCAT(',', BillingEvent.flags, ',') CONTAINS (',ALLOCATION,'),
    'ALLOCATE',
    BillingEvent.reason) AS action,
  BillingEvent.targetId AS domain,
  BillingEvent.domainRepoId AS repositoryId,
  periodYears AS years,
  BillingEvent.currency AS currency,
  amountMinor,
  REGEXP_EXTRACT(cost, ' (.+)') AS amountString,
  ROUND(amountMinor * Currency.conversionToUsd, 2) AS estimatedUsd,
  flags,
  Cancellation.cancellationId IS NOT NULL AS cancelled,
  Cancellation.cancellationTime AS cancellationTime,
FROM (
  -- Subquery for common processing shared across OneTime and Recurring data,
  -- which is parsing the string cost into currency and amountMinor.
  SELECT
    id,
    kind,  -- Needed for joining Cancellations correctly.
    billingTime,
    eventTime,
    clientId,
    tld,
    reason,
    targetId,
    domainRepoId,
    periodYears,
    cost,
    -- TODO(b/19031545): Find cleaner way to parse out currency and amount.
    -- Parse out the currency code as the substring of 'cost' up to the space.
    REGEXP_EXTRACT(cost, '(.+) ') AS currency,
    -- Parse out the amount of minor units by stripping out non-digit chars
    -- (i.e. currency, space, and period) and then converting to integer.
    INTEGER(REGEXP_REPLACE(cost, r'\D+', '')) AS amountMinor,
    -- Convert repeated flags field into flat comma-delimited string field.
    flags,
  FROM (
    -- Extract OneTime records from raw snapshot data.
    SELECT
      __key__.id AS id,
      __key__.kind AS kind,
      billingTime,
      eventTime,
      clientId,
      tld,
      reason,
      targetId,
      -- TODO(b/20828509): see if this can be expressed more cleanly.
      REGEXP_EXTRACT(__key__.path, '"DomainBase", "([^"]+)"') AS domainRepoId,
      periodYears,
      cost,
      GROUP_CONCAT(flags) WITHIN RECORD AS flags,
    FROM (
      SELECT
        *,
        -- TODO(b/20828509): make this robust to multi-part TLDS.
        LAST(SPLIT(targetId, '.')) AS tld
      FROM
        [%SOURCE_DATASET%.OneTime]
      WHERE
        -- Filter out Registry 1.0 data - TODO(b/20828509): remove this.
        __key__.namespace = '')
    WHERE
      -- Filter out prober data.
      tld IN
        (SELECT tld FROM [%DEST_DATASET%.RegistryData] WHERE type = 'REAL')
    ), (
    -- Extract synthetic recurring events from view of Recurring data.
    --
    -- TODO(b/27562876): Drop this section of the query once we verify
    -- that expanded OneTime events via MapReduce are correct and complete.
    SELECT
      id,
      kind,
      billingTime,
      eventTime,
      clientId,
      tld,
      reason,
      targetId,
      domainRepoId,
      periodYears,
      cost,
      flags,
    FROM
      [%DEST_DATASET%.RecurringEventData]
    )
  ) AS BillingEvent

-- Join to pick up billing ID from registrar table.
LEFT JOIN EACH (
  SELECT
    __key__.name AS clientId,
    billingIdentifier,
  FROM
    [%SOURCE_DATASET%.Registrar]
  ) AS Registrar
ON
  BillingEvent.clientId = Registrar.clientId

-- Join to pick up cancellations for billing events.
LEFT JOIN EACH (
  SELECT
    __key__.id AS cancellationId,
    -- Coalesce matching fields from refOneTime and refRecurring (only one or
    -- the other will ever be populated) for joining against referenced event.
    COALESCE(refOneTime.kind, refRecurring.kind) AS cancelledEventKind,
    COALESCE(refOneTime.id, refRecurring.id) AS cancelledEventId,
    -- TODO(b/20828509): see if this can be expressed more cleanly.
    REGEXP_EXTRACT(
      COALESCE(refOneTime.path, refRecurring.path),
      '"DomainBase", "([^"]+)"') AS cancelledEventDomainRepoId,
    eventTime AS cancellationTime,
    billingTime AS cancellationBillingTime,
  FROM (
    SELECT
      *,
      -- TODO(b/20828509): make this robust to multi-part TLDS.
      LAST(SPLIT(targetId, '.')) AS tld
    FROM
      [%SOURCE_DATASET%.Cancellation]
    WHERE
      -- Filter out Registry 1.0 data - TODO(b/20828509): remove this.
      __key__.namespace = ''
        -- TODO(b/27562876): Filter out synthetic OneTime events until we verify
        -- that expanded OneTime events via MapReduce are correct and complete.
        AND syntheticCreationTime IS NULL)
  WHERE
    -- Filter out prober data.
    tld IN
      (SELECT tld FROM [%DEST_DATASET%.RegistryData] WHERE type = 'REAL')
  ) AS Cancellation
ON
  BillingEvent.kind = Cancellation.cancelledEventKind
  AND BillingEvent.id = Cancellation.cancelledEventId
  -- Note: we need to include the repoId here to handle old pre-Registry-2.0
  -- billing events that would have had ID collisions across TLDs.
  AND BillingEvent.domainRepoId = Cancellation.cancelledEventDomainRepoId
  -- Require billing times to match so that cancellations for Recurring events
  -- only apply to the specific recurrence being cancelled.
  AND BillingEvent.billingTime = Cancellation.cancellationBillingTime

-- Join to pick up currency conversion factor.
LEFT JOIN EACH (
  SELECT
    currency,
    conversionToUsd,
  FROM
    [%DEST_DATASET%.Currency]
  ) AS Currency
ON
  BillingEvent.currency = Currency.currency

WHERE
  -- Filter down to whitelisted TLDs that are "billable".
  -- TODO(b/18092292): determine this automatically.
  BillingEvent.tld IN
    (SELECT tld FROM FLATTEN((
      -- %TLDS% is passed in as a comma-delimited string of TLDs.
      SELECT SPLIT('%TLDS%') AS tld FROM (SELECT 1 as unused)), tld))

-- Sort rows to show the latest billed items first.
ORDER BY
  billingTime DESC,
  -- Break ties in billing time using ID then TLD, to be deterministic.
  id,
  tld
