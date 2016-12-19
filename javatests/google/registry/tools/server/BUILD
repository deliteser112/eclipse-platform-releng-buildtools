package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "server",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    deps = [
        "//java/google/registry/groups",
        "//java/google/registry/mapreduce",
        "//java/google/registry/model",
        "//java/google/registry/request",
        "//java/google/registry/tools/server",
        "//java/google/registry/util",
        "//javatests/google/registry/model",
        "//javatests/google/registry/testing",
        "//javatests/google/registry/testing/mapreduce",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_tools_appengine_gcs_client",
        "@com_google_appengine_tools_appengine_mapreduce",
        "@com_google_guava",
        "@com_google_re2j",
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
    test_files = glob(["*Test.java"]),
    deps = [":server"],
)
