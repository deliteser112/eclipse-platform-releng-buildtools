This folder contains experimental Gradle scripts as an alternative to Bazel.
These are work-in-progress and are expected to evolve in the near future.

## Current status

Currently there are two sub-projects, third_party, which contains the
back-ported JUnit 4.13 code; and core, which contains all Nomulus source code.
Gradle can be used to compile and run all Java tests.

Gradle is configured to use the existing Nomulus source tree.

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

Install Gradle 4.10.x on your local host, then run the following commands from
this directory:

```shell
GRADLE_DIR="$(pwd)"

(cd "${GRADLE_DIR}"; gradle init; mkdir third_party core)

cp root.gradle "${GRADLE_DIR}/build.gradle"

cp settings.gradle "${GRADLE_DIR}"

cp third_party.gradle "${GRADLE_DIR}/third_party/build.gradle"

cp core.gradle "${GRADLE_DIR}/core/build.gradle"

cd "${GRADLE_DIR}"

./gradlew build
```

The example above uses the directory with this file as working directory for
Gradle. You may use a different directory for Gradle, by assigning GRADLE_DIR to
the desired path. You will also need to update the following paths in the
.gradle files:

*   In "${GRADLE_DIR}/build.gradle", update allprojects > repositories >
    flatDir > dirs to the correct location.
*   In core/build.gradle, update 'javaSourceDir' and 'javatestSourceDir' to the
    correct locations.
*   In third_party/build.gradle, update SourceSets > main > java > srcDirs to
    the correct locations.

From now on, use './gradlew build' or './gradlew test' build and test your
changes.
