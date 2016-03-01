package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


# Needed for the documentation tests
filegroup(
    name = "flows_files",
    srcs = glob([
        "*.java",
        "**/*.java",
    ]),
)

java_library(
    name = "flows",
    srcs = glob([
        "*.java",
        "**/*.java",
    ]),
    resources = glob(["**/testdata/*.xml"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/dns",
        "//java/com/google/domain/registry/flows",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/security",
        "//java/com/google/domain/registry/security:servlets",
        "//java/com/google/domain/registry/tmch",
        "//java/com/google/domain/registry/util",
        "//java/com/google/domain/registry/xml",
        "//javatests/com/google/domain/registry/model",
        "//javatests/com/google/domain/registry/testing",
        "//javatests/com/google/domain/registry/testing/mapreduce",
        "//javatests/com/google/domain/registry/xml",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine:appengine-testing",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",
    ],
)

# If the flows tests should grow again to the point that they last longer than
# sixty seconds, then shard_count should be tuned. You can binary search for a
# good value that balances time reduction with environmental impact. However,
# any unit test that contains fewer @Test methods than the shard count will
# If you grep for testNothing you can find the existing dummy methods.
GenTestRules(
    name = "GeneratedTestRules",
    default_test_size = "medium",
    jvm_flags = ["-XX:MaxPermSize=256m"],
    shard_count = 4,
    test_files = glob([
        "*Test.java",
        "*/*Test.java",
    ]),
    deps = [":flows"],
)
