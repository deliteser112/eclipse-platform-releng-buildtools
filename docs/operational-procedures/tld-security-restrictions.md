# Security restrictions for TLD

Nomulus provides several security features that allow registries to impose
additional restrictions on which domains are allowed on a TLD and which
registrant/nameservers they can have. The restrictions can be applied to an
entire TLD or on a per-domain basis. This document outlines the various
restrictions available, their intended use case, and how to use them.

## TLD-wide restrictions

Nomulus allows registry administrators to set registrant contact and nameserver
restrictions on a TLD. Use the following command to set the restrictions when
creating or updating a TLD:

```shell
$ nomulus -e production [create_tld | update_tld] \
    --allowed_nameservers {NS1},{NS2},... \
    --allowed_registrants {REG1},{REG2},... \
    {TLD1} {TLD2} ...
```

When nameserver or registrant restrictions are set on a TLD, any domain mutation
flow under that TLD will verify that the supplied nameservers or registrants
constitute a subset of the allowed nameservers or registrants on the TLD. Note
that an empty set is not considered a legitimate subset, and consequently when
the restrictions are set for one property, you **must** provide at least one
corresponding value that is allowed. If no restrictions are set, it is allowed
to create/update domains that do not have nameservers on it. Registrant, on the
other hand, is always required.

## Per-domain nameserver restrictions

Registries can also elect to impose per-domain nameserver restrictions in
Nomulus. This restriction is orthogonal to the TLD-wide nameserver restriction
detailed above, and the allowed nameserver list can be set to an arbitrary list
that is not related to the TLD-wide allowed nameservers list (if any). Any
domain mutation must pass both validations (if applicable). In practice, it is
always recommended to maintain consistency between the two types of lists, by
making the per-domain allowed nameserver list a subset of the TLD-wide one,
because any nameservers that are not included in both lists are effectively
disallowed.

The per-domain allowed nameserver lists are maintained in reserved lists with
reservation type `NAMESERVER_RESTRICTED`, using a csv format, which nameservers
delimited by colons. The following example has two allowed nameservers:

```
internaldomain,NAMESERVER_RESTRICTED,ns1.internal.tld:ns1.internal.tld
```

Reserved lists can also prohibit domain registrations for reasons other than
nameserver restrictions. For more details on reserved lists and how they work,
refer to the doc [here](reserved-list-management.md). Note that multiple
reserved lists can be applied to one TLD, and if they happen to contain
nameserver restrictions to the same label, the resulting restriction set is the
intersection of the all allowed nameserver lists for that label.

## Domain create restriction on closed TLDs

Nomulus offers the ability to "lock-down" a TLD so that domain registration is
by default forbidden unless the domain is whitelisted. The typical use case for
this feature is for a closed TLD that wants to enforce greater security by only
allowing registration of domains that are explicitly allowed. Such restriction
on domain creation is achieved by setting the TLD to be "domain create
restricted". The allowed list of domains are read from reserved lists applied on
the TLD, with `NAMESERVER_RESTRICTED` reservation. This means that each domain
will also need to have explicitly allowed nameservers configured in its reserved
list entry, and the per-domain nameserver validation is performed in related
flows.

To apply domain create restriction when creating/updating a TLD:

```shell
$ nomulus -e production [create_tld | update_tld] \
    --domain_create_restricted {TLD1} {TLD2} ...
```

Note that you do **not** have to set a TLD-wide allowed nameservers list,
because it operates independently from the per-domain nameservers restriction
that `NAMESERVER_RESTRICTED` reservation invokes.

In addition to disabling registration of non-whitelisted domains, setting a TLD
as domain create restricted also applies `SERVER_UPDATE_PROHIBITED` and
`SERVER_TRANSFER_PROHIBITED` status to domains automatically when they are
created. Therefore any domains created under such a TLD is virtually immutable.
For more information on EPP status codes, see
[here](https://tools.ietf.org/html/rfc5731#section-2.3).

The consequence of applying these status code is that no registrar can send
request to modify an existing domain without the registry explicitly allowing it
on a case-by-case basis. If a change does need to be made, the registry must
explicitly remove the status, make changes, and then reapply the status. To
remove/reapply server status, use:

```shell
$ nomulus -e production update_server_locks \
    --[apply | remove] {STATUS1},{STATUS2},... \
    --client {REGISTRAR}
    --n {DOMAIN}
```

Note that domain create restricted is intended for closed TLDs, as such, the
validation only happens in regular domain create/update flows. Domain
application and allocate are usually not applicable to closed TLDs because there
is no sunrise period. Therefore no domain whitelist validation against the
reserved lists is performed during these flows, nor are server prohibited status
applied.
