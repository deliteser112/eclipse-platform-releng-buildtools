package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "tools",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    runtime_deps = [
        # TODO(b/19332643): Remove this dependency when Modules is lazy.
        "@com_google_appengine_api_1_0_sdk//:testonly",
    ],
    deps = [
        "//java/google/registry/module/tools",
        "@com_google_truth",
        "@javax_servlet_api",
        "@junit",
        "@org_mockito_all",
    ],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":tools"],
)
