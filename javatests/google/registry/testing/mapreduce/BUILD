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
        "//javatests/google/registry/testing",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/appengine:appengine-stubs",
        "//third_party/java/appengine:appengine-testing",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/appengine_pipeline",
        "@com_google_code_findbugs_jsr305",
        "@com_google_flogger",
        "@com_google_flogger_system_backend",
        "@com_google_guava",
        "@com_google_truth",
        "@com_google_truth_extensions_truth_java8_extension",
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
