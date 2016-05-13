package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "sheet",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/export/sheet",
        "//java/com/google/domain/registry/model",
        "//java/com/google/gdata:spreadsheet",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",
    ],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":sheet"],
)
