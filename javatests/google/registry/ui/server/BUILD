package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "server",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/ui/forms",
        "//java/google/registry/ui/server",
        "//javatests/google/registry/testing",
        "@com_google_truth",
        "@junit",
        "@org_hamcrest_library",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":server"],
)
