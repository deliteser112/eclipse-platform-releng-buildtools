# Operational procedures

This document covers procedures that are typically used when running a
production registry system.

## OT&E onboarding

## Stackdriver monitoring

[Stackdriver Monitoring](https://cloud.google.com/monitoring/docs/) is used to
instrument internal state within the Nomulus internal environment. This is
broadly called white-box monitoring. Currently, EPP and DNS are instrumented.
The metrics monitored are as follows:

*   `/custom/epp/requests` -- A count of EPP requests, described by command
    name, client id, and return status code.
*   `/custom/epp/processing_time` -- A
    [Distribution](https://cloud.google.com/monitoring/api/ref_v3/rest/v3/TypedValue#Distribution)
    representing the processing time for EPP requests, described by command
    name, client id, and retujrn status code.
*   `/custom/dns/publish_domain_requests` -- A count of publish domain requests,
    described by the target TLD and the return status code from the underlying
    DNS implementation.
*   `/custom/dns/publish_host_requests` -- A count of publish host requests,
    described by the target TLD and the return status code from the underlying
    DNS implementation.

Follow the guide to [set up a Stackdriver
account](https://cloud.google.com/monitoring/accounts/guide) and associate it
with the GCP project containing the Nomulus App Engine app. Once the two have
been linked, monitoring will start automatically. For now, because the
visualization of custom metrics in Stackdriver is embryronic, you can retrieve
and visualize the collected metrics with a script, as described in the guide on
[Reading Time
Series](https://cloud.google.com/monitoring/custom-metrics/reading-metrics) and
the [custom metric code
sample](https://github.com/GoogleCloudPlatform/python-docs-samples/blob/master/monitoring/api/v3/custom_metric.py).

In addition to the included white-box monitoring, black-box monitoring should be
set up to exercise the functionality of the registry platform as a user would
see it. This monitoring should, for example, create a new domain name every few
minutes via EPP and then verify that the domain exists in DNS and WHOIS. For
now, no black-box monitoring implementation is provided with the Nomulus
platform.

## Updating cursors

In most cases, cursors will not advance if a task that utilizes a cursor fails
(so that the task can be retried for that given timestamp). However, there are
some cases where a cursor is updated at the end of a job that produces bad
output (for example, RDE export), and in order to re-run a job, the cursor will
need to be rolled back.

In rare cases it might be useful to roll a cursor forward if there is some bad
data at a given time that prevents a task from completing successfully, and an
acceptable solution is to simply skip the bad data.

Cursors can be updated as follows:

```shell
$ nomulus -e {ENVIRONMENT} update_cursors exampletld --type RDE_STAGING \
    --timestamp 2016-09-01T00:00:00Z
Update Cursor@ahFzfmRvbWFpbi1yZWdpc3RyeXIzCxIPRW50aXR5R3JvdXBSb290Igljcm9zcy10bGQMCxIIUmVnaXN0cnkiB3lvdXR1YmUM_RDE_STAGING
cursorTime -> [2016-09-23T00:00:00.000Z, 2016-09-01T00:00:00.000Z]

Perform this command? (y/N): Y
Updated 1 entities.
```
