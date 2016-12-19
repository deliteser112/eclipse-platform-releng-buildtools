package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "security",
    srcs = glob(
        ["*.java"],
        exclude = glob(["*ServletTest.java"]),
    ),
    deps = [
        "//java/google/registry/security",
        "//java/google/registry/util",
        "//javatests/google/registry/testing",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_testing",
        "@com_google_guava",
        "@com_google_truth",
        "@com_googlecode_json_simple",
        "@javax_servlet_api",
        "@joda_time",
        "@junit",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [
        ":security",
    ],
)
