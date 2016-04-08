package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "bigquery",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    deps = [
        "//apiserving/discoverydata/bigquery:bigqueryv2",
        "//java/com/google/api/client/http",
        "//java/com/google/api/client/json",
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/bigquery",
        "//java/com/google/domain/registry/util",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":bigquery"],
)
