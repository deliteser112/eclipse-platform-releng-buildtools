package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "tools",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/export",
        "//java/com/google/domain/registry/gcs",
        "//java/com/google/domain/registry/groups",
        "//java/com/google/domain/registry/keyring/api",
        "//java/com/google/domain/registry/loadtest",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/request:modules",
        "//java/com/google/domain/registry/tools/mapreduce",
        "//java/com/google/domain/registry/tools/server",
        "//java/com/google/domain/registry/util",
        "//third_party/java/bouncycastle",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)

# This rule is used so bazel can generate "tools_jar_deploy.jar" (which
# contains transitive dependencies) for deployment to App Engine. It MUST
# explicitly depend upon upon anything loaded at runtime, e.g. old servlets
# referenced by the module's web.xml file, that isn't statically linked above.
java_binary(
    name = "tools_jar",
    create_executable = 0,
    runtime_deps = [
        ":tools",
    ],
)
