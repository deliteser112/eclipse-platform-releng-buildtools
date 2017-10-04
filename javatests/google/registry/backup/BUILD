package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "backup",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    deps = [
        "//java/google/registry/backup",
        "//java/google/registry/model",
        "//java/google/registry/util",
        "//javatests/google/registry/testing",
        "//javatests/google/registry/testing/mapreduce",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/truth",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_tools_appengine_gcs_client",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
        "@com_google_guava_testlib",
        "@javax_servlet_api",
        "@joda_time",
        "@junit",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":backup"],
)
