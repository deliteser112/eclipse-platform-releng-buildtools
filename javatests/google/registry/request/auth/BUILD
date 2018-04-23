package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "auth",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    deps = [
        "//java/google/registry/request",
        "//java/google/registry/request/auth",
        "//java/google/registry/security",
        "//javatests/google/registry/testing",
        "//third_party/objectify:objectify-v4_1",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_tools_appengine_gcs_client",
        "@com_google_appengine_tools_sdk",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
        "@com_google_truth",
        "@com_google_truth_extensions_truth_java8_extension",
        "@javax_servlet_api",
        "@junit",
        "@org_mockito_all",
        "@org_mortbay_jetty",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":auth"],
)
