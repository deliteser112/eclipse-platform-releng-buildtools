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
        "//third_party/java/hamcrest",
        "//third_party/java/junit",
        "//third_party/java/truth",
        "//java/google/registry/ui/forms",
        "//java/google/registry/ui/server",
        "//javatests/google/registry/testing",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":server"],
)
