package(
    default_testonly = 1,
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "mapreduce",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/mapreduce",
        "//java/google/registry/model",
        "//java/google/registry/util",
        "//javatests/google/registry/testing",
        "@com_google_appengine_api_1_0_sdk",
        "@com_google_appengine_api_stubs",
        "@com_google_appengine_testing",
        "@com_google_appengine_tools_appengine_mapreduce",
        "@com_google_appengine_tools_appengine_pipeline",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
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
