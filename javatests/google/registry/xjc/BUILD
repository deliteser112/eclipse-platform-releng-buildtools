package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "xjc",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*.xml"]),
    deps = [
        "//java/com/google/domain/registry/util",
        "//java/com/google/domain/registry/xjc",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/hamcrest",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":xjc"],
)
