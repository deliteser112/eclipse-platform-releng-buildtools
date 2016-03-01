# Domain Registry

Domain Registry is a production service for managing registrations on top-level
domains in a shared namespace. Domain Registry runs on [Google App Engine][gae]
and is written primarily in Java. It achieves in a hundred thousand lines of
code what [Jon Postel][postel] used to do on index cards.

This is the software that [Google Registry][google-registry] uses to operate
TLDs such as .GOOGLE, .HOW, .SOY, and .みんな.

### What is a Registry?

When it comes to internet land, ownership flows down the following hierarchy:

1. [ICANN][icann]
2. [Registries][registry] (e.g. Google Registry)
3. [Registrars][registrar] (e.g. Google Domains)
4. Registrants (e.g. you)

A registry is any organization that operates an entire top-level domain. For
example, Verisign controls all the .COM domains and Affilias controls all the
.ORG domains.

### How Scalable is Domain Registry?

We successfully verified that Domain Registry is able to perform 1,000 EPP
"domain creates" per second, with 99th percentile latency at ~3 seconds, and
95th percentile latency at ~1 second. Please note that 1,000 was the highest QPS
our load tester allowed.

In theory, Domain Registry is infinitely scalable. The only limitation is that
each individual EPP resource can only support one write per second, which in
practice, is more like ten. However reads to a single resource are free and
unlimited.

### How Reliable is Domain Registry?

Domain Registry achieves its scalability without sacrificing the level of
correctness an engineer would expect from an ACID SQL database.

Domain Registry is built on top of [Google Cloud Datastore][datastore]. This is
a global NoSQL database that provides an unlimited number of [Paxos][paxos]
entity groups, each of which being able to scale to an unlimited size while
supporting a single local transaction per second. Datastore also supports
distributed transactions that span up to twenty-five entity groups. Transactions
are limited to four minutes and ten megabytes in size. Furthermore, queries and
indexes that span entity groups are always eventually consistent, which means
they could take seconds, and very rarely, days to update. While most online
services find eventual consistency useful, it is not appropriate for a service
conducting financial exchanges.  Therefore Domain Registry has been engineered
to employ performance and complexity tradeoffs that allow strong consistency to
be applied throughout the codebase.

Domain Registry has a commit log system. Commit logs are retained in datastore
for thirty days. They are also streamed to Cloud Storage for backup purposes.
Commit logs are written across one thousand entity group shards, each with a
local timestamp. The commit log system is able to reconstruct a global partial
ordering of transactions, based on these local timestamps. This is necessary in
order to do restores. Each EPP resource entity also stores a map of its past
mutations with 24-hour granularity. This makes it possible to have point-in-time
projection queries with effectively no overhead.

The Registry Data Escrow (RDE) system is also built with reliability in mind.
It executes on top of App Engine task queues, which can be double-executed and
therefore require operations to be idempotent. RDE isn't idempotent. To work
around this, RDE uses datastore transactions to achieve mutual exclusion and
serialization. We call this the "Locking Rolling Cursor Pattern." One benefit of
this pattern, is that if the escrow service should fall into a failure state for
a few days, or weeks, it will automatically catch up on its work once the
problem is resolved. RDE is also able to perform strongly consistent queries
with snapshot isolation across the entire datastore. It does this by sharding
global indexes into entity groups buckets (which can be queried with strong
consistency) and then rewinding the entities to the desired point in time.

The Domain Registry codebase is also well tested. The core packages in the
codebase (model, flows, rde, whois, etc.) have 95% test coverage.


## Setup

All you have to do is [install Bazel][bazel-install] and clone this repository
locally. Once that's done, here are some example commands to get you started:

```sh
# Run all tests
bazel test //java{,tests}/com/google/domain/registry/...

# Run the registry_tool command
bazel run //java/com/google/domain/registry/tool:registry_tool -- --help

# Run the Registrar Console on a local development server
bazel run //javatests/com/google/domain/registry/server -- --help
bazel run //javatests/com/google/domain/registry/server
google-chrome http://localhost:8080/registrar
```


## Services

Domain Registry provides the following IETF standard services.

### Extensible Provisioning Protocol (EPP)

[EPP][epp] is the core service of the registry. It's an XML protocol that's used
by registrars to register domains from the registry on behalf of registrants.
Domain Registry implements this service as an App Engine HTTP servlet listening
on the `/_dr/epp` path. Requests are forwarded to this path by a public-facing
proxy listening on port 700.

To supplement EPP, Domain Registry also provides a public API for performing
domain availability checks. This service listens on the `/check` path.

#### RFCs

- [RFC 5730: EPP](http://tools.ietf.org/html/rfc5730)
- [RFC 5731: EPP Domain Mapping](http://tools.ietf.org/html/rfc5731)
- [RFC 5732: EPP Host Mapping](http://tools.ietf.org/html/rfc5732)
- [RFC 5733: EPP Contact Mapping](http://tools.ietf.org/html/rfc5733)
- [RFC 3915: EPP Grace Period Mapping](http://tools.ietf.org/html/rfc3915)
- [RFC 5734: EPP Transport over TCP](http://tools.ietf.org/html/rfc5734)
- [RFC 5910: EPP DNSSEC Mapping](http://tools.ietf.org/html/rfc5910)
- [Draft: EPP Launch Phase Mapping (Proposed)](http://tools.ietf.org/html/draft-tan-epp-launchphase-11)

### Registry Data Escrow (RDE)

RDE and BRDA are implemented as a cron mapreduce that takes a strongly
consistent point-in-time snapshot of the registration database, turns it into a
gigantic XML file, and uploads it to an SFTP server run by a third party escrow
provider. This happens nightly with RDE and weekly with BRDA.

This service exists for ICANN regulatory purposes. ICANN needs to know that,
should a registry business ever implode, that they can quickly migrate their
TLDs to a different company so that they'll continue to operate.

#### RFCs

- [Draft: Registry Data Escrow Specification](http://tools.ietf.org/html/draft-arias-noguchi-registry-data-escrow-06)
- [Draft: Domain Name Registration Data (DNRD) Objects Mapping](http://tools.ietf.org/html/draft-arias-noguchi-dnrd-objects-mapping-05)
- [Draft: ICANN Registry Interfaces](http://tools.ietf.org/html/draft-lozano-icann-registry-interfaces-05)

### Trademark Clearing House (TMCH)

Domain Registry integrates with ICANN and IBM's MarksDB in order to protect
trademark holders, when new TLDs are being launched.

#### RFCs

- [Draft: TMCH Functional Spec](http://tools.ietf.org/html/draft-lozano-tmch-func-spec-08)
- [Draft: Mark and Signed Mark Objects Mapping](https://tools.ietf.org/html/draft-lozano-tmch-smd-02)

### WHOIS

[WHOIS][whois] is a simple text-based protocol that allows anyone to look up
information about a domain registrant. Domain Registry implements this as an
internal HTTP endpoint running on `/_dr/whois`. A separate proxy running on port
43 forwards requests to that path. Domain Registry also implements a public HTTP
endpoint that listens on the `/whois` path.

#### RFCs

- [RFC 3912: WHOIS Protocol Specification](https://tools.ietf.org/html/rfc3912)
- [RFC 7485: Inventory and Analysis of Registration Objects](http://tools.ietf.org/html/rfc7485)

### Registration Data Access Protocol (RDAP)

RDAP is the new standard for WHOIS. It provides much richer functionality, such
as the ability to perform wildcard searches. Domain Registry makes this HTTP
service available under the `/rdap/...` path.

#### RFCs

- [RFC 7480: RDAP HTTP Usage](http://tools.ietf.org/html/rfc7480)
- [RFC 7481: RDAP Security Services](http://tools.ietf.org/html/rfc7481)
- [RFC 7482: RDAP Query Format](http://tools.ietf.org/html/rfc7482)
- [RFC 7483: RDAP JSON Responses](http://tools.ietf.org/html/rfc7483)
- [RFC 7484: RDAP Finding the Authoritative Registration Data](http://tools.ietf.org/html/rfc7484)


[datastore]: https://cloud.google.com/datastore/docs/concepts/overview
[gae]: https://cloud.google.com/appengine/docs/about-the-standard-environment
[bazel-install]: http://bazel.io/docs/install.html
[epp]: https://en.wikipedia.org/wiki/Extensible_Provisioning_Protocol
[google-registry]: https://www.registry.google/
[gtld]: https://en.wikipedia.org/wiki/Generic_top-level_domain
[icann]: https://en.wikipedia.org/wiki/ICANN
[paxos]: https://en.wikipedia.org/wiki/Paxos_(computer_science)
[postel]: https://en.wikipedia.org/wiki/Jon_Postel
[registrar]: https://en.wikipedia.org/wiki/Domain_name_registrar
[registry]: https://en.wikipedia.org/wiki/Domain_name_registry
[whois]: https://en.wikipedia.org/wiki/WHOIS
