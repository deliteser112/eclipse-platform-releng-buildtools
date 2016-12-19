package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "config",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/config",
        "@com_google_guava",
        "@com_google_truth",
        "@junit",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":config"],
)
