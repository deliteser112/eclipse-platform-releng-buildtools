package(
    default_testonly = 1,
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

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
        "//java/google/registry/config",
        "//java/google/registry/dns",
        "//java/google/registry/flows",
        "//java/google/registry/model",
        "//java/google/registry/monitoring/whitebox",
        "//java/google/registry/pricing",
        "//java/google/registry/request",
        "//java/google/registry/tmch",
        "//java/google/registry/util",
        "//java/google/registry/xml",
        "//javatests/google/registry/model",
        "//javatests/google/registry/testing",
        "//javatests/google/registry/xml",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_testing",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_guava_testlib",
        "@com_google_re2j",
        "@com_google_truth",
        "@com_googlecode_json_simple",
        "@javax_servlet_api",
        "@joda_time",
        "@junit",
        "@org_joda_money",
        "@org_mockito_all",
    ],
)

# If the flows tests should grow again to the point that they last longer than
# sixty seconds, then shard_count should be tuned. You can binary search for a
# good value that balances time reduction with environmental impact. However,
# any unit test that contains fewer @Test methods than the shard count will
# need to be updated to add dummy methods, otherwise blaze will lose its mind.
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
