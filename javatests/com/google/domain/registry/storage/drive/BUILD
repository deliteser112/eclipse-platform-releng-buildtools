package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "drive",
    srcs = glob(["*Test.java"]),
    deps = [
        "//apiserving/discoverydata/drive",
        "//java/com/google/api/client/http",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/storage/drive",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":drive"],
)
