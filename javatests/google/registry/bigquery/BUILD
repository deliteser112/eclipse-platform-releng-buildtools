package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "bigquery",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    deps = [
        "//java/google/registry/bigquery",
        "//java/google/registry/util",
        "//javatests/google/registry/testing",
        "//third_party/java/truth",
        "@com_google_apis_google_api_services_bigquery",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
        "@com_google_http_client",
        "@joda_time",
        "@junit",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":bigquery"],
)
