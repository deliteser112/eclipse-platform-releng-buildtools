# Code structure

This document contains information on the overall structure of the code, and how
particularly important pieces of the system are implemented.

## Dagger dependency injection

## Bazel build system

## Flows

## Commit logs and backups

## Cursors

## Mapreduces

## Actions and servlets

## Foreign key indexes

## Point-in-time accuracy

## Guava

## EPP resource hierarchy

## Poll messages

Poll messages are the mechanism by which EPP handles asynchronous communication
between the registry and registrars. Refer to
[RFC 5730 Section 2.9.2.3](https://tools.ietf.org/html/rfc5730#section-2.9.2.3)
for their protocol specification.

Poll messages are stored by the system as entities in Datastore. All poll
messages have an event time at which they become active; any poll request before
that time will not return the poll message. For example, every domain when
created speculatively enqueues a poll message for the automatic renewal of the
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

*   **`Autorenew`** - A poll message corresponding to an automatic renewal of
    a domain. It recurs annually.
*   **`OneTime`** - A one-time poll message used for everything else.

Queries for poll messages by the registrar are handled in `PollRequestFlow`, and
poll messages are ACKed (and thus deleted) in `PollAckFlow`.

## Security
