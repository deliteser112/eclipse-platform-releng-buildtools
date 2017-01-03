package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "tools",
    srcs = glob([
        "*.java",
    ]),
    resources = glob([
        "testdata/*.*",
    ]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/flows",
        "//java/google/registry/keyring/api",
        "//java/google/registry/model",
        "//java/google/registry/rde",
        "//java/google/registry/request",
        "//java/google/registry/tmch",
        "//java/google/registry/tools",
        "//java/google/registry/tools/params",
        "//java/google/registry/tools/server",
        "//java/google/registry/util",
        "//java/google/registry/xjc",
        "//java/google/registry/xml",
        "//javatests/google/registry/model",
        "//javatests/google/registry/rde",
        "//javatests/google/registry/testing",
        "//javatests/google/registry/xml",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_beust_jcommander",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_remote_api//:link",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
        "@com_google_re2j",
        "@com_google_truth",
        "@com_googlecode_json_simple",
        "@joda_time",
        "@junit",
        "@org_joda_money",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["**/*Test.java"]),
    deps = [":tools"],
)
