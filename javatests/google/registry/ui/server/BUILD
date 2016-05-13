package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "server",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/domain/registry/ui/forms",
        "//java/com/google/domain/registry/ui/server",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/hamcrest",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":server"],
)
