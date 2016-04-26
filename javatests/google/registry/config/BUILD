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
        "//java/com/google/common/annotations",
        "//third_party/java/junit",
        "//third_party/java/truth",
        "//java/google/registry/config",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":config"],
)
