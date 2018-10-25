This folder contains experimental Gradle scripts as an alternative to Bazel for
the open-source Nomulus project. These are work-in-progress and are expected to
evolve in the near future.

All testing is done with Gradle v4.10.2.

## Current status

Currently there are two sub-projects, third_party, which contains the
back-ported JUnit 4.13 code; and core, which contains all Nomulus source code.
Gradle can be used to compile and run all Java tests.

Gradle is configured to use the directory containing this file as root, but use
the existing Nomulus source tree.

Dependencies are mostly the same as in Bazel, with a few exceptions:

*   org.slf4j:slf4j-simple is added to provide a logging implementation in
    tests. Bazel does not need this.
*   com.googlecode.java-diff-utils:diffutils is not included. Bazel needs it for
    Truth's equality check, but Gradle works fine without it.
*   jaxb 2.2.11 is used instead of 2.3 in Bazel, since the latter breaks the
    ant.xjc task. The problem is reportedly fixed in jaxb 2.4.
*   The other dependencies are copied from Nomulus' repository.bzl config.
    *   We still need to verify if there are unused dependencies.
    *   Many dependencies are behind their latest versions.

### Notable Issues

Only single-threaded test execution is allowed, due to race condition over
global resources, such as the local Datastore instance, or updates to the System
properties. This is a new problem with Gradle, which does not provide as much
test isolation as Bazel. We are exploring solutions to this problem.

Test suites (RdeTestSuite and TmchTestSuite) are ignored to avoid duplicate
execution of tests. Neither suite performs any shared test setup routine, so it
is easier to exclude the suite classes than individual test classes.

Since Gradle does not support hierarchical build files, all file sets (e.g.,
resources) must be declared at the top, in root project config or the
sub-project configs.

## Initial Setup

Install Gradle on your local host, then run the following commands from this
directory:

```shell
# One-time command to add gradle wrapper:
gradle wrapper

# Start the build:
./gradlew build
```

From now on, use './gradlew build' or './gradlew test' to build and test your
changes.

To upgrade to a new Gradle version for this project, use:

```shell
gradle wrapper --gradle-version version-number
```
