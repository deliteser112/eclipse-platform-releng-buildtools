package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "whitebox",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/bigquery",
        "//java/google/registry/config",
        "//java/google/registry/mapreduce",
        "//java/google/registry/model",
        "//java/google/registry/monitoring/whitebox",
        "//java/google/registry/util",
        "//javatests/google/registry/testing",
        "//javatests/google/registry/testing/mapreduce",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_apis_google_api_services_bigquery",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_api_stubs",
        "@com_google_appengine_testing",
        "@com_google_auto_value",
        "@com_google_guava",
        "@com_google_guava_testlib",
        "@com_google_http_client",
        "@com_google_truth",
        "@javax_servlet_api",
        "@joda_time",
        "@junit",
        "@org_joda_money",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    medium_tests = ["VerifyEntityIntegrityActionTest"],
    test_files = glob(["*Test.java"]),
    deps = [":whitebox"],
)
