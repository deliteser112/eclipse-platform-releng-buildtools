# App Engine architecture

This document contains information on the overall architecture of the Domain
Registry project as it is implemented in App Engine.

## Services

The Domain Registry contains three
[services](https://cloud.google.com/appengine/docs/python/an-overview-of-app-engine),
which were previously called modules in earlier versions of App Engine.  The
services are: default (also called front-end), backend, and tools.  Each service
runs independently in a lot of ways, including that they can be upgraded
individually, their log outputs are separate, and their servers and configured
scaling are separate as well.

### Default service

The default service is responsible for all registrar-facing
[EPP](https://en.wikipedia.org/wiki/Extensible_Provisioning_Protocol) command
traffic, all user-facing WHOIS and RDAP traffic, and the admin and registrar web
consoles, and is thus the most important service.  If the service has any
problems and goes down or stops servicing requests in a timely manner, it will
begin to impact users immediately.  Requests to the default service are handled
by the `FrontendServlet`, which provides all of the endpoints exposed in
`FrontendRequestComponent`.

### Backend service

The backend service is responsible for executing all regularly scheduled
background tasks (using cron) as well as all asynchronous tasks.  Requests to
the backend service are handled by the `BackendServlet`, which provides all of
the endpoints exposed in `BackendRequestComponent`.  These include tasks for
generating/exporting RDE, syncing the trademark list from TMDB, exporting
backups, writing out DNS updates, handling asynchronous contact and host
deletions, writing out commit logs, exporting metrics to BigQuery, and many
more.  Issues in the backend service will not immediately be apparent to end
users, but the longer it is down, the more obvious it will become that
user-visible tasks such as DNS and deletion are not being handled in a timely
manner.

The backend service is also where all MapReduces run, which includes some of the
aforementioned tasks such as RDE and asynchronous resource deletion, as well as
any one-off data migration MapReduces.  Consequently, the backend service should
be sized to support not just the normal ongoing DNS load but also the load
incurred by MapReduces, both scheduled (such as RDE) and on-demand (asynchronous
contact/host deletion).

### Tools service

The tools service is responsible for servicing requests from the `registry_tool`
command line tool, which provides administrative-level functionality for
developers and tech support employees of the registry.  It is thus the least
critical of the three services.  Requests to the tools service are handled by
the `ToolsServlet`, which provides all of the endpoints exposed in
`ToolsRequestComponent`.  Some example functionality that this service provides
includes the server-side code to update premium lists, run EPP commands from the
tool, and manually modify contacts/hosts/domains/and other resources.  Problems
with the tools service are not visible to users.

## Task queues

[Task queues](https://cloud.google.com/appengine/docs/java/taskqueue/) in App
Engine provide an asynchronous way to enqueue tasks and then execute them on
some kind of schedule.  There are two types of queues, push queues and pull
queues.  Tasks in push queues are always executing up to some throttlable limit.
Tasks in pull queues remain there indefinitely until the queue is polled by code
that is running for some other reason.  Essentially, push queues run their own
tasks while pull queues just enqueue data that is used by something else.  Many
other parts of App Engine are implemented using task queues.  For example,
[App Engine cron](https://cloud.google.com/appengine/docs/java/config/cron) adds
tasks to push queues at regularly scheduled intervals, and the
[MapReduce framework](https://cloud.google.com/appengine/docs/java/dataprocessing/)
adds tasks for each phase of the MapReduce algorithm.

The Domain Registry project uses a particular pattern of paired push/pull queues
that is worth explaining in detail.  Push queues are essential because App
Engine's architecture does not support long-running background processes, and so
push queues are thus the fundamental building block that allows asynchronous and
background execution of code that is not in response to incoming web requests.
However, they also have limitations in that they do not allow batch processing
or grouping.  That's where the pull queue comes in.  Regularly scheduled tasks
in the push queue will, upon execution, poll the corresponding pull queue for a
specified number of tasks and execute them in a batch.  This allows the code to
execute in the background while taking advantage of batch processing.

Particulars on the task queues in use by the Domain Registry project are
specified in the `queue.xml` file.  Note that many push queues have a direct
one-to-one correspondence with entries in `cron.xml` because they need to be
fanned-out on a per-TLD or other basis (see the Cron section below for more
explanation).  The exact queue that a given cron task will use is passed as the
query string parameter "queue" in the url specification for the cron task.

Here are the task queues in use by the system.  All are push queues unless
explicitly marked as otherwise.

* `bigquery-streaming-metrics` -- Queue for metrics that are asynchronously
  streamed to BigQuery in the `Metrics` class.  Tasks are enqueued during EPP
  flows in `EppController`.  This means that there is a lag of a few seconds to
  a few minutes between when metrics are generated and when they are queryable
  in BigQuery, but this is preferable to slowing all EPP flows down and blocking
  them on BigQuery streaming.
* `brda` -- Queue for tasks to upload weekly Bulk Registration Data Access
  (BRDA) files to a location where they are available to ICANN.  The
  `RdeStagingReducer` (part of the RDE MapReduce) creates these tasks at the end
  of generating an RDE dump.
* `delete-commits` -- Cron queue for tasks to regularly delete commit logs that
  are more than thirty days stale.  These tasks execute the
  `DeleteOldCommitLogsAction`.
* `dns-cron` (cron queue) and `dns-pull` (pull queue) -- A push/pull pair of
  queues.  Cron regularly enqueues tasks in dns-cron each minute, which are then
  executed by `ReadDnsQueueAction`, which leases a batch of tasks from the pull
  queue, groups them by TLD, and writes them as a single task to `dns-publish`
  to be published to the configured DNS writer for the TLD.
* `dns-publish` -- Queue for batches of DNS updates to be pushed to DNS writers.
* `export-bigquery-poll` -- Queue for tasks to query the success/failure of a
  given BigQuery export job.  Tasks are enqueued by `BigqueryPollJobAction`.
* `export-commits` -- Queue for tasks to export commit log checkpoints.  Tasks
  are enqueued by `CommitLogCheckpointAction` (which is run every minute by
  cron) and executed by `ExportCommitLogDiffAction`.
* `export-reserved-terms` -- Cron queue for tasks to export the list of reserved
  terms for each TLD.  The tasks are executed by `ExportReservedTermsAction`.
* `export-snapshot` -- Cron and push queue for tasks to load a Datastore
  snapshot that was stored in Google Cloud Storage and export it to BigQuery.
  Tasks are enqueued by both cron and `CheckSnapshotServlet` and are executed by
  both `ExportSnapshotServlet` and `LoadSnapshotAction`.
* `export-snapshot-poll` -- Queue for tasks to check that a Datastore snapshot
  has been successfully uploaded to Google Cloud Storage (this is an
  asynchronous background operation that can take an indeterminate amount of
  time).  Once the snapshot is successfully uploaded, it is imported into
  BigQuery.  Tasks are enqueued by `ExportSnapshotServlet` and executed by
  `CheckSnapshotServlet`.
* `export-snapshot-update-view` -- Queue for tasks to update the BigQuery views
  to point to the most recently uploaded snapshot.  Tasks are enqueued by
  `LoadSnapshotAction` and executed by `UpdateSnapshotViewAction`.
* `flows-async` -- Queue for asynchronous tasks that are enqueued during EPP
  command flows.  Currently all of these tasks correspond to invocations of any
  of the following three MapReduces: `DnsRefreshForHostRenameAction`,
  `DeleteHostResourceAction`, or `DeleteContactResourceAction`.
* `group-members-sync` -- Cron queue for tasks to sync registrar contacts (not
  domain contacts!) to Google Groups.  Tasks are executed by
  `SyncGroupMembersAction`.
* `load[0-9]` -- Queues used to load-test the system by `LoadTestAction`.  These
  queues don't need to exist except when actively running load tests (which is
  not recommended on production environments).  There are ten of these queues to
  provide simple sharding, because the Domain Registry system is capable of
  handling significantly more Queries Per Second than the highest throttle limit
  available on task queues (which is 500 qps).
* `lordn-claims` and `lordn-sunrise` -- Pull queues for handling LORDN exports.
  Tasks are enqueued synchronously during EPP commands depending on whether the
  domain name in question has a claims notice ID.
* `marksdb` -- Queue for tasks to verify that an upload to NORDN was
  successfully received and verified.  These tasks are enqueued by
  `NordnUploadAction` following an upload and are executed by
  `NordnVerifyAction`.
* `nordn` -- Cron queue used for NORDN exporting.  Tasks are executed by
  `NordnUploadAction`, which pulls LORDN data from the `lordn-claims` and
  `lordn-sunrise` pull queues (above).
* `rde-report` -- Queue for tasks to upload RDE reports to ICANN following
  successful upload of full RDE files to the escrow provider.  Tasks are
  enqueued by `RdeUploadAction` and executed by `RdeReportAction`.
* `rde-upload` -- Cron queue for tasks to upload already-generated RDE files
  from Cloud Storage to the escrow provider.  Tasks are executed by
  `RdeUploadAction`.
* `sheet` -- Queue for tasks to sync registrar updates to a Google Sheets
  spreadsheet.  Tasks are enqueued by `RegistrarServlet` when changes are made
  to registrar fields and are executed by `SyncRegistrarsSheetAction`.

## Environments

The domain registry codebase comes pre-configured with support for a number of
different environments, all of which are used in Google's registry system.
Other registry operators may choose to user more or fewer environments,
depending on their needs.

The different environments are specified in `RegistryEnvironment`.  Most
correspond to a separate App Engine app except for `UNITTEST` and `LOCAL`, which
by their nature do not use real environments running in the cloud.  The
recommended naming scheme for the App Engine apps that has the best possible
compatibility with the codebase and thus requires the least configuration is to
pick a name for the production app and then suffix it for the other
environments.  E.g., if the production app is to be named 'registry-platform',
then the sandbox app would be named 'registry-platform-sandbox'.

The full list of environments supported out-of-the-box, in descending order from
real to not, is:

* `PRODUCTION` -- The real production environment that is actually running live
  TLDs.  Since the Domain Registry is a shared registry platform, there need
  only ever be one of these.
* `SANDBOX` -- A playground environment for external users to test commands in
  without the possibility of affecting production data.  This is the environment
  new registrars go through
  [OT&E](https://www.icann.org/resources/unthemed-pages/registry-agmt-appc-e-2001-04-26-en)
  in.  Sandbox is also useful as a final sanity check to push a new prospective
  build to and allow it to "bake" before pushing it to production.
* `QA` -- An internal environment used by business users to play with and sign
  off on new features to be released.  This environment can be pushed to
  frequently and is where manual testers should be spending the majority of
  their time.
* `CRASH` -- Another environment similar to QA, except with no expectations of
  data preservation.  Crash is used for testing of backup/restore (which brings
  the entire system down until it is completed) without affecting the QA
  environment.
* `ALPHA` -- The developers' playground.  Experimental builds are routinely
  pushed here in order to test them on a real app running on App Engine.  You
  may end up wanting multiple environments like Alpha if you regularly
  experience contention (i.e. developers being blocked from testing their code
  on Alpha because others are already using it).
* `LOCAL` -- A fake environment that is used when running the app locally on a
  simulated App Engine instance.
* `UNITTEST` -- A fake environment that is used in unit tests, where everything
  in the App Engine stack is simulated or mocked.

## Release process

The following is a recommended release process based on Google's several years
of experience running a production registry using this codebase.

1. Developers write code and associated unit tests verifying that the new code
   works properly.
2. New features or potentially risky bug fixes are pushed to Alpha and tested by
   the developers before being committed to the source code repository.
3. New builds are cut and first pushed to Sandbox.
4. Once a build has been running successfully in Sandbox for a day with no
   errors, it can be pushed to Production.
5. Repeat once weekly, or potentially more often.

## Cron tasks

All [cron tasks](https://cloud.google.com/appengine/docs/java/config/cron) are
specified in `cron.xml` files, with one per environment.  There are more tasks
that execute in Production than in other environments, because tasks like
uploading RDE dumps are only done for the live system.

Most cron tasks use the `TldFanoutAction` which is accessed via the
`/_dr/cron/fanout` URL path.  This action, which is run by the BackendServlet on
the backend service, fans out a given cron task for each TLD that exists in the
registry system, using the queue that is specified in the `cron.xml` entry.
Because some tasks may be computationally intensive and could risk spiking
system latency if all start executing immediately at the same time, there is a
`jitterSeconds` parameter that spreads out tasks over the given number of
seconds.  This is used with DNS updates and commit log deletion.

The reason the `TldFanoutAction` exists is that a lot of tasks need to be done
separately for each TLD, such as RDE exports and NORDN uploads.  It's simpler to
have a single cron entry that will create tasks for all TLDs than to have to
specify a separate cron task for each action for each TLD (though that is still
an option).

## Datastore entities

## Cloud Storage buckets

## Web.xml

## Cursors
