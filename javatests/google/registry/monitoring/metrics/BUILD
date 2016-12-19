package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "metrics",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/monitoring/metrics",
        "@com_google_api_client",
        "@com_google_apis_google_api_services_monitoring",
        "@com_google_guava",
        "@com_google_http_client",
        "@com_google_http_client_jackson2",
        "@com_google_truth",
        "@joda_time",
        "@junit",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [
        ":metrics",
    ],
)
