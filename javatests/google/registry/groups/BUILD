package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "groups",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/cron",
        "//java/google/registry/groups",
        "//java/google/registry/model",
        "//javatests/google/registry/testing",
        "@com_google_api_client",
        "@com_google_apis_google_api_services_admin_directory",
        "@com_google_apis_google_api_services_groupssettings",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_api_stubs",
        "@com_google_appengine_testing",
        "@com_google_guava",
        "@com_google_http_client",
        "@com_google_http_client_jackson2",
        "@com_google_truth",
        "@javax_servlet_api",
        "@joda_time",
        "@junit",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":groups"],
)
