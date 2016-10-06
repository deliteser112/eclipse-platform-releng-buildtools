# Configuration

There are multiple different kinds of configuration that go into getting a
working registry system up and running. Broadly speaking, configuration works in
two ways -- globally, for the entire sytem, and per-TLD. Global configuration is
managed by editing code and deploying a new version, whereas per-TLD
configuration is data that lives in Datastore in `Registry` entities, and is
updated by running `nomulus` commands without having to deploy a new version.

[TOC]

## Initial configuration

Here's a checklist of things that need to be configured upon initial
installation of the project:

*   Create Google Cloud Storage buckets (see the [App Engine architecture
    guide](./app-engine-architecture.md)).
*   Modify `ConfigModule.java` and set project-specific settings such as product
    name (see below).
*   Copy and edit `ProductionRegistryConfigExample.java` with your
    project-specific settings (see below).

## Environments

Before getting into the details of configuration, it's important to note that a
lot of configuration is environment-dependent. It is common to see `switch`
statements that operate on the current `RegistryEnvironment`, and return
different values for different environments. This is especially pronounced in
the `UNITTEST` and `LOCAL` environments, which don't run on App Engine at all.
As an example, some timeouts may be long in production and short in unit tests.

See the "App Engine architecture" documentation for more details on environments
as used in the domain registry.

## App Engine configuration

App Engine configuration isn't covered in depth in this document as it is
thoroughly documented in the [App Engine configuration docs][app-engine-config].
The main files of note that come pre-configured along with the domain registry
are:

*   `cron.xml` -- Configuration of cronjobs
*   `web.xml` -- Configuration of URL paths on the webserver
*   `appengine-web.xml` -- Overall App Engine settings including number and type
    of instances
*   `datastore-indexes.xml` -- Configuration of entity indexes in Datastore
*   `queue.xml` -- Configuration of App Engine task queues
*   `application.xml` -- Configuration of the application name and its services

Cron, web, and queue are covered in more detail in the "App Engine architecture"
doc, and the rest are covered in the general App Engine documentation.

If you are not writing new code to implement custom features, is unlikely that
you will need to make any modifications beyond simple changes to
`application.xml` and `appengine-web.xml`. If you are writing new features, it's
likely you'll need to add cronjobs, URL paths, Datastore indexes, and task
queues, and thus edit those associated XML files.

## Global configuration

There are two different mechanisms by which global configuration is managed:
`RegistryConfig` (the old way) and `ConfigModule` (the new way). Ideally there
would just be one, but the required code cleanup that hasn't been completed yet.
If you are adding new options, prefer adding them to `ConfigModule`.

**`RegistryConfig`** is an interface, of which you write an implementing class
containing the configuration values. `RegistryConfigLoader` is the class that
provides the instance of `RegistryConfig`, and defaults to returning
`ProductionRegistryConfigExample`.

In order to create a configuration specific to your registry, we recommend
copying the `ProductionRegistryConfigExample` class to a new class that will not
be shared publicly, setting the `google.registry.config` system property in the
`appengine-web.xml` files to the fully qualified class name of that new class
so that `RegistryConfigLoader` will load it instead, and then editing said new
class to add your specific configuration options. There is one
`appengine-web.xml` file per service (so three per environment). The same
configuration class must be used for each service, but different ones can be
used for different environments.

The `RegistryConfig` class has documentation on all of the methods that should
be sufficient to explain what each option is, and
`ProductionRegistryConfigExample` provides an example value for each one. Some
example configuration options in this interface include the App Engine project
ID, the number of days to retain commit logs, the names of various Cloud Storage
bucket names, and URLs for some required services both external and internal.

**`ConfigModule`** is a Dagger module that provides injectable configuration
options (some of which come from `RegistryConfig` above, but most of which do
not). This is preferred over `RegistryConfig` for new configuration options
because being able to inject configuration options is a nicer pattern that makes
for cleaner code. Some configuration options that can be changed in this class
include timeout lengths and buffer sizes for various tasks, email addresses and
URLs to use for various services, more Cloud Storage bucket names, and WHOIS
disclaimer text.

## Sensitive global configuration

Some configuration values, such as PGP private keys, are so sensitive that they
should not be written in code as per the configuration methods above, as that
would pose too high a risk of them accidentally being leaked, e.g. in a source
control mishap. We use a secret store to persist these values in a secure
manner, and abstract access to them using the `Keyring` interface.

The `Keyring` interface contains methods for all sensitive configuration values,
which are primarily credentials used to access various ICANN and ICANN-
affiliated services (such as RDE). These values are only needed for real
production registries and PDT environments. If you are just playing around with
the platform at first, it is OK to put off defining these values until
necessary. To that end, a `DummyKeyringModule` is included that simply provides
an `InMemoryKeyring` populated with dummy values for all secret keys. This
allows the codebase to compile and run, but of course any actions that attempt
to connect to external services will fail because none of the keys are real.

To configure a production registry system, you will need to write a replacement
module for `DummyKeyringModule` that loads the credentials in a secure way, and
provides them using either an instance of `InMemoryKeyring` or your own custom
implementation of `Keyring`. You then need to replace all usages of
`DummyKeyringModule` with your own module in all of the per-service components
in which it is referenced. The functions in `PgpHelper` will likely prove useful
for loading keys stored in PGP format into the PGP key classes that you'll need
to provide from `Keyring`, and you can see examples of them in action in
`DummyKeyringModule`.

## Per-TLD configuration

`Registry` entities, which are persisted to Datastore, are used for per-TLD
configuration. They contain any kind of configuration that is specific to a TLD,
such as the create/renew price of a domain name, the pricing engine
implementation, the DNS writer implementation, whether escrow exports are
enabled, the default currency, the reserved label lists, and more. The `nomulus
update_tld` command is used to set all of these options. See the [admin tool
documentation](./admin-tool.md) for more information, as well as the
command-line help for the `update_tld` command. Unlike global configuration
above, per-TLD configuration options are stored as data in the running system,
and thus do not require code pushes to update.

[app-engine-config]: https://cloud.google.com/appengine/docs/java/configuration-files
