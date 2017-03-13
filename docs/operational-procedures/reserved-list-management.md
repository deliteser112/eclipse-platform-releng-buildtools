# Managing reserved lists

Reserved lists are static lists of labels that are blocked from being registered
for various reasons, usually because of potential abuse.

## Reserved list file format

Reserved lists are handled in a similar way to [premium
lists](./premium-list-management.md), except that instead of each label having
a price, it has a reservation type. The valid values for reservation types are:

*   **`UNRESERVED`** - The default value for any label that isn't reserved.
    Labels that aren't explictly under any other status implictly have this
    value.
*   **`NAMESERVER_RESTRICTED`** - Only nameservers included here can be set on a
    domain with this label. If the a label in this type exists on multiple
    reserved lists that are applied to the same TLD. The set of allowed
    nameservers for that label in that TLD is the intersection of all applicable
    nameservers.
*   **`ALLOWED_IN_SUNRISE`** - The label can be registered during the sunrise
    period by a registrant with a valid claim but it is reserved thereafter.
*   **`MISTAKEN_PREMIUM`** - The label is reserved because it was mistakenly put
    on a premium list. It may be registered during sunrise by a registrant with
    a valid claim but is reserved thereafter.
*   **`RESERVED_FOR_ANCHOR_TENANT`** - The label is reserved for the use of an
    anchor tenant, and can only be registered by someone sending along the EPP
    passcode specified here at time of registration. If a label has different
    passcodes in different lists that are applied to the same TLD, an error will
    occur.
*   **`NAME_COLLISION`** - The label is reserved because it is on an [ICANN
    collision
    list](https://www.icann.org/resources/pages/name-collision-2013-12-06-en).
    It may be registered during sunrise by a registrant with a valid claim but
    is reserved thereafter.
*   **`FULLY_BLOCKED`** - The label is fully reserved, no further reason
    specified.

The reservation types are listed in order of increasing precedence, but if a
label is included in different lists that are applied to a single TLD, all
reservation types of the label are returned when queried. The order of the
reservation types only affects the message a domain check EPP request receives,
which is the one with the highest precedence. E.g. a label with name collision
reservation type in one list and allowed in sunrise reservation type in another
list will have both reservation types, but domain check will report that the
label is reserved due to name collision (with message "Cannot be delegated"). In
general `FULLY_BLOCKED` is by far the most widely used reservation type for
typical TLD use cases.

Here's an example of a small reserved list. Note that
`RESERVED_FOR_ANCHOR_TENANT` has a third entry on the line, being the EPP
passcode required to register the domain (`hunter2` in this case); and that
`NAMESERVER_RESERVED` also has a third entry, a colon separated list of
nameservers that the label can be delegated to:

```
reserveddomain,FULLY_BLOCKED
availableinga,ALLOWED_IN_SUNRISE
fourletterword,FULLY_BLOCKED
acmecorp,RESERVED_FOR_ANCHOR_TENANT,hunter2
internaldomain,NAMESERVER_RESTRICTED,ns1.internal.tld:ns1.internal.tld
```

There are two types of reserved lists: Those that are intended to apply to a
specifc TLD, and are thus prefixed with the name of the TLD followed by an
underscore, and those that can be applied to any TLD, and are prefixed with
`common_`. For example, a list of reserved labels on the TLD `exampletld` might
be named `exampletld_blocked-names.txt`, whereas a similar list intended to
apply to multiple TLDs might be named `common_blocked-names.txt`. Note that
these naming conventions are enforced by the tooling used to create and apply
reserved lists (see subsequent sections).

## Creating a reserved list

Once the file containing the list of reserved terms is created, run the
`create_reserved_list` command to load it into Datastore as follows. For the
purposes of this example, we are creating a common reserved list named
"common_bad-words".

```shell
$ nomulus -e {ENVIRONMENT} create_reserved_list -n common_bad-words \
    -i common_bad-words.txt
[ ... snip long confirmation prompt ... ]
Perform this command? (y/N): y
Updated 1 entities.
```

`-n` is the name of the reserved list to create, and `-i` is the input file
containing the list. Note that if `-n` is omitted, the list name is inferred
from the input of the filename minus its file extension. It is recommended to
store all lists such that the filename and list name are identical.

## Updating a reserved list

To update the contents of an existing reserved list, make changes to the .txt
file containing the reserved list entries, then pass it as input to the
`update_reserved_list` command as follows:

```shell
$ nomulus -e {ENVIRONMENT} update_reserved_list -n common_bad-words \
    -i common_bad-words.txt
[ ... snip diff of changes to list entries ... ]
Perform this command? (y/N): y
Updated 1 entities.
```

Note that, like the create command, the name of the list is inferred from the
filename if the `-n` parameter is omitted.

## Applying a reserved list to a TLD

Separate from the management of the contents of individual reserved lists,
reserved lists must also be applied to a TLD. Unlike premium lists, for which
each TLD may only have a single list applied, a TLD can have any number of
reserved lists applied. The list of reserved labels for a TLD is the union of
all applied reserved lists, using the precedence rules described earlier when a
label appears in more than one list.

To add a reserved list to a TLD, run the `update_tld` command with the following
parameter:

```shell
$ nomulus -e {ENVIRONMENT} update_tld exampletld \
    --add_reserved_lists common_bad-words
Update Registry@exampletld
reservedLists -> [null, [Key<?>(EntityGroupRoot("cross-tld")/ReservedList("common_bad-words"))]]
Perform this command? (y/N): y
Updated 1 entities.
```

The `--add_reserved_lists` parameter can take a comma-delimited list of reserved
list names if you are applying multiple reserved lists to a TLD. There is also a
`--remove_reserved_lists` parameter that functions as you might expect.

Naming rules are enforced: reserved lists that start with `common_` can be
applied to any TLD (though they don't automatically apply to all TLDs), whereas
reserved lists that start with the name of a TLD can only be applied to the TLD
with that name.

## Checking which reserved lists are applied to a TLD

The `get_tld` command shows which reserved lists (if any) are applied to a TLD,
along with lots of other information about that TLD which is not relevant to our
purposes here. It is used as follows:

```shell
$ nomulus -e {ENVIRONMENT} get_tld exampletld
[ ... snip ... ]
reservedLists=[Key<?>(EntityGroupRoot("cross-tld")/ReservedList("common_bad-words"))]
[ ... snip ... ]
```

## Listing all available reserved lists

The `list_reserved_lists` command is used to list all reserved lists in
Datastore. It takes no arguments and displays a simple list of reserved lists in
newline-delimited format as follows:

```shell
$ nomulus -e {ENVIRONMENT} list_reserved_lists
common_bad-words
exampletld_some-other-list
```
