package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "config",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/domain/registry/config",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":config"],
)
