# Operational procedures

This document covers procedures that are typically used when running a
production registry system.

[TOC]

## OT&E onboarding

## Managing static premium price lists

### Premium list file format

The default domain registry codebase comes with a
`StaticPremiumListPricingEngine` that determines premium prices of domain labels
(i.e. the part of the domain name without the TLD) by checking for their
presence on a list of prices in Datastore. `registry_tool` is used to load and
update these lists from flat text files. The format of this list is simple: It
is a newline-delimited CSV text file with each line containing the label and its
price (including currency specifier in ISO-4217 format). As an example:

```
premium,USD 100
superpremium,USD 500
```

By convention, premium lists are named after the TLD they apply to (with one
premium list per TLD). If the above example applied to the `exampletld` example,
then the file would be saved as `exampletld.txt` and the domain name
`premium.exampletld` would cost $100. It is also possible to have a single
premium list that is used on multiple TLDs, for which a different naming
convention would be used.

It is recommended that you store your premium list .txt files in a backed-up
location that is accessible to all members of your team (ideally in a source
control system for revision tracking). These files should be thought of as the
canonical versions of your premium lists. Note that there is no provided way to
reconstruct a premium list .txt file from the premium list that is loaded into
Datastore (though in principle it would be easy to do by writing a tool to do
so), so don't lose those .txt files.

### Creating a premium list

Once the file containing the premium prices is ready, run the
`create_premium_list` command to load it into Datastore as follows:

```shell
$ registry_tool -e {ENVIRONMENT} create_premium_list -n exampletld \
    -i exampletld.txt

You are about to save the premium list exampletld with 2 items:
Perform this command? (y/N): y
Successfully saved premium list exampletld
```

`-n` is the name of the list to be created, and `-i` is the input filename. Note
that the convention of naming premium lists after the TLD they are intended to
be used for to is enforced unless the override parameter `-o` is passed, which
allows premium lists to be created with any name.

### Updating a premium list

If the premium list already exists and you want to update it with new prices
from a text file, the procedure is exactly the same, except using the
`update_premium_list` command as follows:

```shell
$ registry_tool -e {ENVIRONMENT} update_premium_list -n exampletld \
    -i exampletld.txt

You are about to save the premium list exampletld with 2 items:
Perform this command? (y/N): y
Successfully saved premium list exampletld
```

### Applying a premium list to a TLD

Separate from the management of the contents of individual premium lists, a
premium list must first be applied to a TLD before it will take effect. You will
only need to do this when first creating a premium list; once it has been
applied, it stays applied, and updates to the list are effective automatically.
Note that each TLD can have no more than one premium list applied to it. To
apply a premium list to a TLD, run the `update_tld` command with the following
parameter:

```shell
$ registry_tool -e {ENVIRONMENT} update_tld exampletld --premium_list exampletld
Update Registry@exampletld
premiumList -> [null, Key<?>(EntityGroupRoot("cross-tld")/PremiumList("exampletld"))]

Perform this command? (y/N): y
Updated 1 entities.
```

### Checking which premium list is applied to a TLD

The `get_tld` command shows which premium list is applied to a TLD (along with
all other information about a TLD). It is used as follows:

```shell
$ registry_tool -e {ENVIRONMENT} get_tld exampletld
[ ... snip ... ]
premiumList=Key<?>(EntityGroupRoot("cross-tld")/PremiumList("exampletld"))
[ ... snip ... ]
```

### Listing all available premium lists

The `list_premium_lists` command is used to list all premium lists in Datastore.
It takes no arguments and displays a simple list of premium lists as follows:

```shell
$ registry_tool -e {ENVIRONMENT} list_premium_lists
exampletld
someotherlist
```

## Managing reserved lists

Reserved lists are static lists of labels that are blocked from being registered
for various reasons, usually because of potential abuse.

### Reserved list file format

Reserved lists are handled in a similar way to premium lists (see above), except
that rather than each label having a price, it has a reservation type. The valid
values for reservation types are:

*   **`UNRESERVED`** - The default value for any label that isn't reserved.
    Labels that aren't explictly under any other status implictly have this
    value.
*   **`ALLOWED_IN_SUNRISE`** - The label can be registered during the sunrise
    period by a registrant with a valid claim but it is reserved thereafter.
*   **`MISTAKEN_PREMIUM`** - The label is reserved because it was mistakenly put
    on a premium list. It may be registered during sunrise by a registrant with
    a valid claim but is reserved thereafter.
*   **`RESERVED_FOR_ANCHOR_TENANT`** - The label is reserved for the use of an
    anchor tenant, and can only be registered by someone sending along the EPP
    passcode specified here at time of registration.
*   **`NAME_COLLISION`** - The label is reserved because it is on an [ICANN
    collision
    list](https://www.icann.org/resources/pages/name-collision-2013-12-06-en).
    It may be registered during sunrise by a registrant with a valid claim but
    is reserved thereafter.
*   **`FULLY_BLOCKED`** - The label is fully reserved, no further reason
    specified.

The reservation types are listed in order of increasing precedence, so if a
label is included on the same list multiple lists, or on different lists that
are applied to a single TLD, whichever reservation type is later in the list
takes precedence. E.g. a label being fully blocked in one list always supersedes
it being allowed in sunrise from another list. In general `FULLY_BLOCKED` is by
far the most widely used reservation type for typical TLD use cases.

Here's an example of a small reserved list. Note that
`RESERVED_FOR_ANCHOR_TENANT` is the only reservation type that has a third entry
on the line, that entry being the EPP passcode required to register the domain
(`hunter2` in this case):

```
reserveddomain,FULLY_BLOCKED
availableinga,ALLOWED_IN_SUNRISE
fourletterword,FULLY_BLOCKED
acmecorp,RESERVED_FOR_ANCHOR_TENANT,hunter2
```

There are two types of reserved lists: Those that are intended to apply to a
specifc TLD, and are thus prefixed with the name of the TLD followed by an
underscore, and those that can be applied to any TLD, and are prefixed with
`common_`. For example, a list of reserved labels on the TLD `exampletld` might
be named `exampletld_blocked-names.txt`, whereas a similar list intended to
apply to multiple TLDs might be named `common_blocked-names.txt`. Note that
these naming conventions are enforced by the tooling used to create and apply
reserved lists (see subsequent sections).

### Creating a reserved list

Once the file containing the list of reserved terms is created, run the
`create_reserved_list` command to load it into Datastore as follows. For the
purposes of this example, we are creating a common reserved list named
"common_bad-words".

```shell
$ registry_tool -e {ENVIRONMENT} create_reserved_list -n common_bad-words \
    -i common_bad-words.txt
[ ... snip long confirmation prompt ... ]
Perform this command? (y/N): y
Updated 1 entities.
```

`-n` is the name of the reserved list to create, and `-i` is the input file
containing the list. Note that if `-n` is omitted, the list name is inferred
from the input of the filename minus its file extension. It is recommended to
store all lists such that the filename and list name are identical.

### Updating a reserved list

To update the contents of an existing reserved list, make changes to the .txt
file containing the reserved list entries, then pass it as input to the
`update_reserved_list` command as follows:

```shell
$ registry_tool -e {ENVIRONMENT} update_reserved_list -n common_bad-words \
    -i common_bad-words.txt
[ ... snip diff of changes to list entries ... ]
Perform this command? (y/N): y
Updated 1 entities.
```

Note that, like the create command, the name of the list is inferred from the
filename if the `-n` parameter is omitted.

### Applying a reserved list to a TLD

Separate from the management of the contents of individual reserved lists,
reserved lists must also be applied to a TLD. Unlike premium lists, for which
each TLD may only have a single list applied, a TLD can have any number of
reserved lists applied. The list of reserved labels for a TLD is the union of
all applied reserved lists, using the precedence rules described earlier when a
label appears in more than one list.

To add a reserved list to a TLD, run the `update_tld` command with the following
parameter:

```shell
$ registry_tool -e {ENVIRONMENT} update_tld exampletld \
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

### Checking which reserved lists are applied to a TLD

The `get_tld` command shows which reserved lists (if any) are applied to a TLD,
along with lots of other information about that TLD which is not relevant to our
purposes here. It is used as follows:

```shell
$ registry_tool -e {ENVIRONMENT} get_tld exampletld
[ ... snip ... ]
reservedLists=[Key<?>(EntityGroupRoot("cross-tld")/ReservedList("common_bad-words"))]
[ ... snip ... ]
```

### Listing all available reserved lists

The `list_reserved_lists` command is used to list all reserved lists in
Datastore. It takes no arguments and displays a simple list of reserved lists in
newline-delimited format as follows:

```shell
$ registry_tool -e {ENVIRONMENT} list_reserved_lists
common_bad-words
exampletld_some-other-list
```

## StackDriver monitoring
