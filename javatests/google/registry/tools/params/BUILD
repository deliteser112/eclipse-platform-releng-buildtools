package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "params",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/domain/registry/tools/params",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/hamcrest",
        "//third_party/java/jcommander",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":params"],
)
