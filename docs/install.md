# Installation

This document covers the steps necessary to download, build, and deploy Nomulus.

[TOC]

## Prerequisites

You will need the following programs installed on your local machine:

*   A recent version of the [Java 7 JDK][java-jdk7] (note that Java 8 support
    should be coming to App Engine soon).
*   [Bazel](http://bazel.io/), which is the build system that Nomulus uses. The
    minimum required version is 0.3.1.
*   [Google App Engine SDK for Java][app-engine-sdk], especially `appcfg`, which
    is a command-line tool that runs locally that is used to communicate with
    the App Engine cloud.
*   [Git](https://git-scm.com/) version control system.

**Note:** The prerequisites and steps in this document are only known to work
and have only been tested on Linux. They might work with some alterations on
other operating systems.

## Download the code

Start off by using git to download the latest version from the [Nomulus GitHub
page](https://github.com/google/nomulus). In the future we may support more
stable releases, but for now, just download `HEAD` of the master branch as
follows:

```shell
$ git clone git@github.com:google/nomulus.git
Cloning into 'nomulus'...
[ .. snip .. ]
$ cd nomulus
$ ls
apiserving       CONTRIBUTORS  java        LICENSE    scripts
AUTHORS          docs          javascript  python     third_party
CONTRIBUTING.md  google        javatests   README.md  WORKSPACE
```

The most important directories are:

*   `docs` -- the documentation (including this install guide)
*   `java/google/registry` -- all of the source code of the main project
*   `javatests/google/registry` -- all of the tests for the project
*   `python` -- Some Python reporting scripts
*   `scripts` -- Scripts for configuring development environments

Everything else, especially `third_party`, contains dependencies that are used
by the project.

## Build the project and run tests

The first step is to build the project, and verify that this completes
successfully. This will also download and install dependencies.

```shell
$ bazel --batch build //java{,tests}/google/registry/...
INFO: Found 584 targets...
[ .. snip .. ]
INFO: Elapsed time: 124.433s, Critical Path: 116.92s
```

There may be some warnings thrown, but if there are no errors, then you can
proceed. Next, run the tests to verify that all expected functionality succeeds
in your build.

```shell
$ nice bazel --batch test //javatests/google/registry/... \
  --local_resources=1000,3,1.0
Executed 360 out of 360 tests: 360 tests pass.
```

**Note:** The tests can be pretty resource intensive, so experiment with
different values of parameters to optimize between low running time and not
slowing down your computer too badly. Refer to the [Bazel User
Manual](https://www.bazel.io/versions/master/docs/bazel-user-manual.html) for
more information.

## Deploy the code to App Engine

First, [create an
application](https://cloud.google.com/appengine/docs/java/quickstart) on App
Engine to deploy to, and set up `appcfg` to connect to it.

You are going to need to configure a variety of things before a working
installation can be deployed (see the Configuration guide for that). It's
recommended to at least confirm that the default version of the code can be
pushed at all first before diving into that, with the expectation that things
won't work properly until they are configured.

All of the [EAR](https://en.wikipedia.org/wiki/EAR_\(file_format\)) and
[WAR](https://en.wikipedia.org/wiki/WAR_\(file_format\)) files for the different
environments, which were built in the previous step, are outputted to the
`bazel-genfiles` directory as follows:

```shell
$ (cd bazel-genfiles/java/google/registry && ls *.ear)
registry_alpha.ear  registry.ear        registry_sandbox.ear
registry_crash.ear  registry_local.ear

$ (cd bazel-genfiles/java/google/registry && ls *.war)
mandatory_stuff.war           registry_default_local.war
registry_backend_alpha.war    registry_default_sandbox.war
registry_backend_crash.war    registry_default.war
registry_backend_local.war    registry_tools_alpha.war
registry_backend_sandbox.war  registry_tools_crash.war
registry_backend.war          registry_tools_local.war
registry_default_alpha.war    registry_tools_sandbox.war
registry_default_crash.war    registry_tools.war
```

Note that there is one EAR file per environment (production is the one without
an environment in the file name), whereas there is one WAR file per service per
environment, with there being three services in total: default, backend, and
tools.

Then, use `appcfg` to [deploy the WAR
files](https://cloud.google.com/appengine/docs/java/tools/uploadinganapp):

```shell
$ cd /path/to/downloaded/appengine/app
$ /path/to/appcfg.sh update /path/to/registry_default.war
$ /path/to/appcfg.sh update /path/to/registry_backend.war
$ /path/to/appcfg.sh update /path/to/registry_tools.war
```

## Create test entities

Once the code is deployed, an optional next step is to play around with creating
some fake entities in the registry, including a TLD, a registrar, a domain, a
contact, and a host. Note: Do this on a non-production environment! All commands
below use `nomulus` to interact with the running registry system; see the
documentation on `nomulus` for additional information on it. We'll assume that
all commands below are running in the `alpha` environment; if you named your
environment differently, then use that everywhere that `alpha` appears.

### Create a TLD

Pick the name of a TLD to create. For the purposes of this example we'll use
"example", which conveniently happens to be an ICANN reserved string, meaning
it'll never be created for real on the Internet at large.

```shell
$ nomulus -e alpha create_tld example --roid_suffix EXAMPLE \
  --initial_tld_state GENERAL_AVAILABILITY --tld_type TEST
[ ... snip confirmation prompt ... ]
Perform this command? (y/N): y
Updated 1 entities.
```

*   `-e` is the environment name (`alpha` in this example).
*   `create_tld` is the subcommand to create a TLD. The TLD name is "example"
    which happens to be an ICANN reserved string, and therefore "example" can
    never be created on the Internet at large.
*   `--initial_tld_state` defines the intital state of the TLD.
    `GENERAL_AVAILABILITY`, in the case of our example, allows you to
    immediately create domain names by bypassing the sunrise and landrush domain
    registration periods.
*   `--tld_type` is the type of TLD. `TEST` identifies that the TLD is for
    testing purposes, where `REAL` identifies the TLD is a live TLD.
*   `roid_suffix` is the suffix that will be used for repository ids of domains
    on the TLD. This suffix must be all uppercase and a maximum of eight ASCII
    characters and can be set t the upper-case equivalent of our TLD name (if it
    is 8 characters or fewer), such as "EXAMPLE." You can also abbreviate the
    upper-case TLD name down to 8 characters. Refer to the [gTLD Registry
    Advisory: Correction of non-compliant ROIDs][roids] for further information.

### Create a registrar

Now we need to create a registrar and give it access to operate on the example
TLD. For the purposes of our example we'll name the registrar "Acme".

```shell
$ nomulus -e alpha create_registrar acme --name 'ACME Corp' \
  --registrar_type TEST --password hunter2 \
  --icann_referral_email blaine@acme.example --street '123 Fake St' \
  --city 'Fakington' --state MA --zip 12345 --cc US --allowed_tlds example
[ ... snip confirmation prompt ... ]
Perform this command? (y/N): y
Updated 1 entities.
Skipping registrar groups creation because only production and sandbox
support it.
```

Where:

*   `create_registrar` is the subcommand to create a registrar. The argument you
    provide ("acme") is the registrar id, called the client identifier, that is
    the primary key used to refer to the registrar both internally and
    externally.
*   `--name` indicates the display name of the registrar, in this case `ACME
    Corp`.
*   `--registrar_type` is the type of registrar. `TEST` identifies that the
    registrar is for testing purposes, where `REAL` identifies the registrar is
    a real live registrar.
*   `--password` is the password used by the registrar to log in to the domain
    registry system.
*   `--icann_referral_email` is the email address associated with the initial
    creation of the registrar. This address cannot be changed.
*   `--allowed_tlds` is a comma-delimited list of top level domains where this
    registrar has access.

### Create a contact

Now we want to create a contact, as a contact is required before a domain can be
created. Contacts can be used on any number of domains across any number of
TLDs, and contain the information on who owns or provides technical support for
a TLD. These details will appear in WHOIS queries.

```shell
$ nomulus -e alpha create_contact -c acme --id abcd1234 \
  --name 'John Smith' --street '234 Fake St' --city 'North Fakington' \
  --state MA --zip 23456 --cc US --email jsmith@e.mail
[ ... snip EPP response ... ]
```

Where:

*   `create_contact` is the subcommand to create a contact.
*   `-c` is used to define the registrar. The `-c` option is used with most
    `registry_tool` commands to specify the id of the registrar executing the
    command. Contact, domain, and host creation all work by constructing an EPP
    message that is sent to the registry, and EPP commands need to run under the
    context of a registrar. The "acme" registrar that was created above is used
    for this purpose.
*   `--id` is the contact id, and is referenced elsewhere in the system (e.g.
    when a domain is created and the admin contact is specified).
*   `--name` is the display name of the contact, which is usually the name of a
    company or of a person.

The address and `email` fields are required to create a contact.

### Create a host

Hosts are used to specify the IP addresses (either v4 or v6) that are associated
with a given nameserver. Note that hosts may either be in-bailiwick (on a TLD
that this registry runs) or out-of-bailiwick. In-bailiwick hosts may
additionally be subordinate (a subdomain of a domain name that is on this
registry). Let's create an out-of-bailiwick nameserver, which is the simplest
type.

```shell
$ nomulus -e alpha create_host -c acme --host ns1.google.com
[ ... snip EPP response ... ]
```

Where:

*   `create_host` is the subcommand to create a host.
*   `--host` is the name of the host.
*   `--addresses` (not used here) is the comma-delimited list of IP addresses
    for the host in IPv4 or IPv6 format, if applicable.

Note that hosts are required to have IP addresses if they are subordinate, and
must not have IP addresses if they are not subordinate.

### Create a domain

To tie it all together, let's create a domain name that uses the above contact
and host.

```shell
$ nomulus -e alpha create_domain -c acme --domain fake.example \
  --admin abcd1234 --tech abcd1234 --registrant abcd1234 \
  --nameservers ns1.google.com
[ ... snip EPP response ... ]
```

Where:

*   `create_domain` is the subcommand to create a domain name.
*   `-c` is used to define the registrar.
*   `--domain` is used to identify the domain name to be created.
*   `--admin` is the administrative contact's id.
*   `--tech` is the technical contact's id.
*   `--registrant` is the registrant contact's id.
*   `--nameservers` identifies the host.

Note how the same contact id is used for the administrative, technical, and
registrant contact. It is common for domain names to use the same details for
all contacts on a domain name.

### Verify test entities using WHOIS

To verify that everything worked, let's query the WHOIS information for
fake.example:

```shell
$ nomulus -e alpha whois_query fake.example
[ ... snip WHOIS response ... ]
```

You should see all of the information in WHOIS that you entered above for the
contact, nameserver, and domain.

[app-engine-sdk]: https://cloud.google.com/appengine/downloads#Google_App_Engine_SDK_for_Java
[java-jdk7]: http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
[roids]: https://www.icann.org/resources/pages/correction-non-compliant-roids-2015-08-26-en
