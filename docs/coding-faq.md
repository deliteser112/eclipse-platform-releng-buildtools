# Coding FAQ

This file is a motley assortment of informational emails generated in response
to questions from development partners.

## Which entities are backed up using commit logs?

Short answer: Check the file `backup_kinds.txt`.

Long answer: There are really two axes. Our custom Ofy actually doesn't
condition commit logging on the `@Entity` class being mutated. Rather, it
conditions based on:

1.  the methods you're invoking
1.  annotations on the class being mutated

**Methods:** The standard `save()` and `delete()` methods always write commit
logs. The alternate `saveWithoutBackup()` and `deleteWithoutBackup()` methods
never write commit logs (and have appropriately scary names to avoid accidental
use). This makes it clear what you're getting at the callsite.

**Annotations:** There are regular `@Entity`-annotated entities,
`@VirtualEntity`-annotated entities, and `@NotBackedUp`-annotated entities. An
`@VirtualEntity` is a "virtual entity" that just serves to construct parent keys
for other entities (e.g. `EppResourceIndexBucket`) and is never written to
datastore itself. An `@NotBackedUp`-annotated entity is one that specifically
shouldn't be backed up (like the commit log entities themselves).

We don't actually prevent you from not-backing-up a regular entity, because
sometimes that's necessary (e.g. in the restore logic, and some other
miscellaneous places). We do prevent you from trying to save/delete an
`@VirtualEntity` (with or without backups) or save/delete an `@NotBackedUp`
entity with backups (i.e. using the regular `save()`/`delete()` calls), since
these are always usage errors.

We went with an `@NotBackedUp` annotation versus an `@BackedUp` annotation
largely because the not-backed-up case is more of the special case (as explained
above, the annotation itself doesn't directly turn backups on or off). To keep
track of what we're intending to back up, we:

1.  synthesize a list of all the backed-up kinds by filtering the registered
    entity classes to remove those with `@VirtualEntity` and `@NotBackedUp`
    annotations:
    [`ExportConstants`](https://github.com/google/nomulus/blob/master/java/google/registry/export/ExportConstants.java#L82),
1.  check this list into the repo:
    [`backup_kinds.txt`](https://github.com/google/nomulus/blob/master/javatests/google/registry/export/backup_kinds.txt), then
1.  run an enforcement test to make sure it is up to date:
    [`ExportConstantsTest`](https://github.com/google/nomulus/blob/master/javatests/google/registry/export/ExportConstantsTest.java#L55).

## How do I mock Google Cloud Storage in tests?

AppEngine's GCS client automatically switches over to a local implementation,
[`GcsServiceFactory`](https://github.com/GoogleCloudPlatform/appengine-gcs-client/blob/master/java/src/main/java/com/google/appengine/tools/cloudstorage/GcsServiceFactory.java#L61),
for tests.

So rather than mocking GCS-related stuff at all, just use the fake local
implementation. This is what our tests should be doing; see
[`ExportCommitLogDiffActionTest`](https://github.com/google/nomulus/blob/master/javatests/google/registry/backup/ExportCommitLogDiffActionTest.java#L70).

Very rarely there have been cases where we've needed something beyond that (e.g.
to test against GCS being eventually consistent). In that case, rather than
mocking GcsUtils, you'd need to create a real instance of it but pass in a
mocked-out GcsService instance. Those are a bit of a pain to make since
GcsServiceImpl itself is also final (and not code we control), but you could
roll your own implementation by hand, or cheat and use a reflective proxy, as we
do in
[`GcsDiffFileListerTest`](https://github.com/google/domain-registry/blob/master/javatests/google/registry/backup/GcsDiffFileListerTest.java#L112).

## How do I test authentication on the SDK Development Server?

*Can someone explain how `GaeUserIdConverter.convertEmailAddressToGaeUserId()`
actually does the conversion? I see it's doing a save/load(/delete) of a
`GaeUserIdConverter`, which contains a `User`. Does Objectify do some magic on
load to look up the real GAE user ID from the email address? In trying to get
the registry to run in the SDK Development Server, I am seeing the wrong user ID
when adding a new RegistryContact using the command line tool.*

The [App Engine development
server](https://cloud.google.com/appengine/docs/python/tools/using-local-server)
is not particularly robust; it appears that it always returns userId
185804764220139124118 for any authenticated user, as per [this StackOverflow
thread](http://stackoverflow.com/questions/30524328/what-user-is-provided-by-app-engine-devserver).

For testing purposes, it might suffice to just create a RegistrarContact with
that userId by hand somehow, so that you can log in. In the longer term, if we
switch to Google Sign-In, this specific problem would go away, but based on the
above, it looks like OAuthService doesn't really work on the dev server either.

So for testing actual "real" authentication, you'd want to use an alpha instance
rather than the development server. We don't use the development server very
much internally for this reason.

## Do you support RDAP?

We are working on an implementation of the Registry Data Access Protocol (RDAP),
ICANN's proposed successor to WHOIS, which provides similar data to WHOIS, but
in a structured format. The standard is defined in RFCs 7480 through 7484:

*   [RFC 7480: HTTP Usage in the Registration Data Access Protocol
    (RDAP)](https://tools.ietf.org/html/rfc7480)
*   [RFC 7481: Security Services for the Registration Data Access Protocol
    (RDAP)](https://tools.ietf.org/html/rfc7481)
*   [RFC 7482: Registration Data Access Protocol (RDAP) Query
    Format](https://tools.ietf.org/html/rfc7482)
*   [RFC 7483: JSON Responses for the Registration Data Access Protocol
    (RDAP)](https://tools.ietf.org/html/rfc7483)
*   [RFC 7484: Finding the Authoritative Registration Data (RDAP)
    Service](https://tools.ietf.org/html/rfc7484)

Some gaps in the implementation remain, but most of the functionality is
currently available. If you access this endpoint on a running Nomulus system:

`https://{PROJECT-ID}.appspot.com/rdap/domains?name=ex*`

it should search for all domains that start with "ex", returning the results in
JSON format. This functionality is still under development, so it is quite
possible that the format of returned data will change over time, but the basic
structure should be the same, as defined by RFCs 7480 through 7484. Request
paths which ought to mostly work (though no guarantees yet):

```
/rdap/domain/abc.tld
/rdap/nameserver/ns1.abc.tld
/rdap/entity/ROID
/rdap/entity/registrar-iana-identifier
/rdap/domains?name=abc.tld
/rdap/domains?name=abc*
/rdap/domains?name=abc*.tld
/rdap/domains?nsLdhName=ns1.abc.tld
/rdap/domains?nsLdhName=ns*
/rdap/domains?nsIp=1.2.3.4
/rdap/nameservers?name=ns*.abc.tld
/rdap/nameservers?ip=1.2.3.4
/rdap/entities?fn=John*
/rdap/entities?handle=ROI*
/rdap/entities?handle=registrar-iana-identifier
```

The wildcard searches allow only trailing wildcards, with the exception that you
can specify a TLD after the domain name wildcard (e.g. abc*.tld), and you can
specify .domain.tld after the nameserver wildcard (e.g. ns*.domain.tld). But you
can't do anything else, like searching for nameservers with ns*.tld. When using
a wildcard, we currently require a prefix of at least two characters, to avoid
having someone search for *. There are other limitations to the system which we
plan to address in the future.

## How do I embed a new class in an existing Datastore entity class?

In Objectify 4.x, which we are pinned to, you have to annotate a class with
@Embed in order to natively nest it within an entity, e.g. as a field within an
@Entity class. This behavior is different from how things work in Objectify 5.x,
which is what the current documentation reflects. Unfortunately the 4.x docs are
hard to access, but you can search this old copy for "embed" to see the details:

[https://raw.githubusercontent.com/objectify/objectify-legacy-wiki/v4/Entities.wiki](https://raw.githubusercontent.com/objectify/objectify-legacy-wiki/v4/Entities.wiki)

So you'd have to annotate your subobject with @Embed for it to work. It's
possible in Objectify 4.x to have a class that's both @Embed and @Entity, but
it'd be a little unorthodox. Usually it'd be cleaner to either make it purely
@Embed (so purely a container for data within the parent entity) or purely
@Entity, in which case you wouldn't use it as a field of the other @Entity but
would instead refer to it by key, e.g. you'd store a Key<MySubObject> instead.
For objects which don't change very often, it is preferable to use the @Entity
approach.

## How do I resolve Bazel package visibility problems?

As a short-term solution, you can disable the bazel visibility checking with
[`--nocheck_visibility`](https://www.bazel.io/versions/master/docs/bazel-user-manual.html#flag--check_visibility).
We are investigating ways to clarify the way that custom functionality is
expected to be added, which will involve changing the package visibility
accordingly.
