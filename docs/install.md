# Installation

Information on how to download and install the Domain Registry project and get a
working running instance.

## Prerequisites

* A recent version of the
[Java 7 JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
(note that Java 8 support should be coming to App Engine soon).
* [Bazel](http://bazel.io/), which is the buld system that
the Domain Registry project uses.  The minimum required version is 0.3.1.
* [Google App Engine SDK for Java](https://cloud.google.com/appengine/downloads#Google_App_Engine_SDK_for_Java),
especially `appcfg`, which is a command-line tool that runs locally that is used
to communicate with the App Engine cloud.
* [Create an application](https://cloud.google.com/appengine/docs/java/quickstart)
  on App Engine to deploy to, and set up `appcfg` to connect to it.

## Downloading the code

Start off by grabbing the latest version from the
[Domain Registry project on GitHub](https://github.com/google/domain-registry).
This can be done either by cloning the Git repo (if you expect to make code
changes to contribute back), or simply by downloading the latest release as a
zip file.  This guide will cover cloning from Git, but should work almost
identically for downloading the zip file.

    $ git clone git@github.com:google/domain-registry.git
    Cloning into 'domain-registry'...
    [ .. snip .. ]
    $ cd domain-registry
    $ ls
    apiserving       CONTRIBUTORS  java        LICENSE    scripts
    AUTHORS          docs          javascript  python     third_party
    CONTRIBUTING.md  google        javatests   README.md  WORKSPACE

The most important directories are:
* `docs` -- the documentation (including this install guide)
* `java/google/registry` -- all of the source code of the main project
* `javatests/google/registry` -- all of the tests for the project
* `python` -- Some Python reporting scripts
* `scripts` -- Scripts for configuring development environments

Everything else, especially `third_party`, contains dependencies that are used
by the project.

## Building and verifying the code

The first step is to verify that the project successfully builds.  This will
also download and install dependencies.

    $ bazel --batch build //java{,tests}/google/registry/...
    INFO: Found 584 targets...
    [ .. snip .. ]
    INFO: Elapsed time: 124.433s, Critical Path: 116.92s

There may be some warnings thrown, but if there are no errors, then you are good
to go.  Next, run the tests to verify that everything works properly.  The tests
can be pretty resource intensive, so experiment with different values of
parameters to optimize between low running time and not slowing down your
computer too badly.

    $ nice bazel --batch test //javatests/google/registry/... \
      --local_resources=1000,3,1.0
    Executed 360 out of 360 tests: 360 tests pass.

## Running a development instance locally

`RegistryTestServer` is a lightweight test server for the registry that is
suitable for running locally for development.  It uses local versions of all
Google Cloud Platform dependencies, when available.  Correspondingly, its
functionality is limited compared to a Domain Registry instance running on an
actual App Engine instance.  To see its command-line parameters, run:

    $ bazel run //javatests/google/registry/server -- --help

Then to fire up an instance of the server, run:

    $ bazel run //javatests/google/registry/server {your params}

Once it is running, you can interact with it via normal `registry_tool`
commands, or view the registrar console in a web browser by navigating to
http://localhost:8080/registrar .

## Deploying the code

You are going to need to configure a variety of things before a working
installation can be deployed (see the Configuration guide for that).  It's
recommended to at least confirm that the default version of the code can be
pushed at all first before diving into that, with the expectation that things
won't work properly until they are configured.

All of the [EAR](https://en.wikipedia.org/wiki/EAR_(file_format\)) and
[WAR](https://en.wikipedia.org/wiki/WAR_(file_format\)) files for the different
environments, which were built in the previous step, are outputted to the
`bazel-genfiles` directory as follows:

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

Note that there is one EAR file per environment (production is the one without
an environment in the file name), whereas there is one WAR file per service per
environment, with there being three services in total: default, backend, and
tools.

Then, use `appcfg` to [deploy the WAR files](https://cloud.google.com/appengine/docs/java/tools/uploadinganapp):

    $ cd /path/to/downloaded/appengine/app
    $ /path/to/appcfg.sh update /path/to/registry_default.war
    $ /path/to/appcfg.sh update /path/to/registry_backend.war
    $ /path/to/appcfg.sh update /path/to/registry_tools.war
