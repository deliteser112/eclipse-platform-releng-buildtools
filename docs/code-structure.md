# Code structure

This document contains information on the overall structure of the code, and how
particularly important pieces of the system are implemented.

## Dagger dependency injection

## Bazel build system

## Flows

## Commit logs and backups

## Cursors

Cursors are `DateTime` pointers used to ensure rolling transactional isolation
of various reporting and other maintenance operations. Utilizing a `Cursor`
within an operation ensures that instances in time are processed exactly once
for a given task, and that tasks can catch up from any failure states at any
time.

Cursors are rolled forward at the end of successful tasks, are not rolled
forward in the case of failure, and can be manually set backwards using the
`nomulus update_cursors` command to reprocess a past action.

The following cursor types are defined:

*   **`BRDA`** - BRDA (thin) escrow deposits
*   **`RDE_REPORT`** - XML RDE report uploads
*   **`RDE_STAGING`** - RDE (thick) escrow deposit staging
*   **`RDE_UPLOAD`** - RDE (thick) escrow deposit upload
*   **`RDE_UPLOAD_SFTP`** - Cursor that tracks the last time we talked to the
    escrow provider's SFTP server for a given TLD.
*   **`RECURRING_BILLING`** - Expansion of `Recurring` (renew) billing events
    into `OneTime` events.

All `Cursor` entities in Datastore contain a `DateTime` that represents the next
timestamp at which an operation should resume processing and a `CursorType` that
identifies which operation the cursor is associated with. In many cases, there
are multiple cursors per operation; for instance, the cursors related to RDE
reporting, staging, and upload are per-TLD cursors. To accomplish this, each
`Cursor` also has a scope, a `Key<ImmutableObject>` to which the particular
cursor applies (this can be e.g. a `Registry` or any other `ImmutableObject` in
datastore, depending on the operation). If the `Cursor` applies to the entire
registry environment, it is considered a global cursor and has a scope of
`EntityGroupRoot.getCrossTldKey()`.

Cursors are singleton entities by type and scope. The id for a `Cursor` is a
deterministic string that consists of the websafe string of the Key of the scope
object concatenated with the name of the name of the cursor type, separated by
an underscore.

## Mapreduces

Nomulus uses the [App Engine MapReduce
framework](https://github.com/GoogleCloudPlatform/appengine-mapreduce/wiki/1-MapReduce)
extensively, both for a variety of regularly scheduled background tasks and for
one-off maintenance tasks. The MapReduce framework comes with a web UI for
viewing the status of ongoing and completed tasks.

Most MapReduces in Nomulus work by mapping over all entities of a given set of
Datastore kind(s) (e.g. domains, contacts, etc.). All of the MapReduces in
Nomulus are run by the `MapreduceRunner` class, which provides a standard set of
ways to set the number of mapper and reducer shards. It is common to run
map-only MapReduces when reducers aren't needed; these are supported as well.

The main complication with MapReduces is that the mapper and reducer classes are
required to be serializable as a consequence of how work is sharded out,
pasued/resumed, and moved around. All fields on these classes must therefore be
either `Serializable` or `transient`. This also means that dependency injection
is of limited use -- the best you can do is to `@Inject` serializable fields on
the entire MapReduce `Action`, and then set them manually on the mapper/reducer
classes in their constructor.

## Actions and servlets

## Foreign key indexes

## Point-in-time accuracy

## Guava

The Nomulus codebase makes extensive use of the
[Guava](https://github.com/google/guava) libraries. These libraries provide
idiomatic, well-tested, and performant add-ons to the JDK. There are several
libraries in particular that you should familiarize yourself with, as they are
used extensively throughout the codebase:

*   [Immutable
    Collections](https://github.com/google/guava/wiki/ImmutableCollectionsExplained):
    Immutable collections are a useful defensive programming technique. When an
    Immutable collection type is used as a parameter type, it immediately
    indicates that the given collection will not be modified in the method.
    Immutable collections are also more memory-efficient than their mutable
    counterparts, and are inherently thread-safe.

    Immutable collections are constructed one of three ways:

    *   Using a `Builder`: used when the collection will be built iteratively in
        a loop.
    *   With the `of` method: used when constructing the collection with a
        handful of elements. Most commonly used when creating collections
        representing constants, like lookup tables or whitelists.
    *   With the `copyOf` method: used when constructing the method from a
        reference to another collection. Used to defensively copy a mutable
        collection (like a return value from an external library) to an
        immutable collection.

*   [Optional](https://github.com/google/guava/wiki/UsingAndAvoidingNullExplained#optional):
    The `Optional<T>` class is used as a container for nullable values. It is
    most often used as return value, as an explicit indicator that the return
    value may be absent, thereby making a `null` return value an obvious error.

*   [Preconditions](https://github.com/google/guava/wiki/PreconditionsExplained):
    Preconditions are used defensively, in order to validate parameters and
    state upon entry to a method.

In addition to Guava, the codebase also extensively uses
[AutoValue](https://github.com/google/auto) value classes. `AutoValue` value
type objects are immutable and have sane default implementations of `toString`,
`hashCode`, and `equals`. They are often used as parameters and return values to
encapsulate related values together.

## EPP resource hierarchy

## Poll messages

Poll messages are the mechanism by which EPP handles asynchronous communication
between the registry and registrars. Refer to [RFC 5730 Section
2.9.2.3](https://tools.ietf.org/html/rfc5730#section-2.9.2.3) for their protocol
specification.

Poll messages are stored by the system as entities in Datastore. All poll
messages have an event time at which they become active; any poll request before
that time will not return the poll message. For example, every domain when
created enqueues a speculative poll message for the automatic renewal of the
domain a year later. This poll message won't be delivered until that year
elapses, and if some change to the domain occurs prior to that point, such as it
being deleted, then the speculative poll message will be deleted and thus never
delivered. Other poll messages are effective immediately, e.g. the poll message
generated for the owning registrar when another registrar requests the transfer
of a domain. These messages are written out with an event time of when they were
created, and will thus be delivered whenever the registrar next polls for
messages.

`PollMessage` is the abstract base class for the two different types of poll
messages that extend it:

*   **`Autorenew`** - A poll message corresponding to an automatic renewal of a
    domain. It recurs annually.
*   **`OneTime`** - A one-time poll message used for everything else.

Queries for poll messages by the registrar are handled in `PollRequestFlow`, and
poll messages are ACKed (and thus deleted) in `PollAckFlow`.

## Security
