package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "rdap",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*.json"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/model",
        "//java/google/registry/rdap",
        "//java/google/registry/request",
        "//javatests/google/registry/testing",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
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
    medium_tests = ["RdapDomainSearchActionTest"],
    test_files = glob(["*Test.java"]),
    deps = [":rdap"],
)
