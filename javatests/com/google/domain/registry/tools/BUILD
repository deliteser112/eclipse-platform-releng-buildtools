package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

internal_files = [
    "brda_copy_files_test.py",
    "tld_watcher_test.py",
]


java_library(
    name = "tools",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*.*"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/common/reflect",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/flows",
        "//java/com/google/domain/registry/keyring/api",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/rde",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/tmch",
        "//java/com/google/domain/registry/tools",
        "//java/com/google/domain/registry/tools/params",
        "//java/com/google/domain/registry/tools/server",
        "//java/com/google/domain/registry/util",
        "//java/com/google/domain/registry/xjc",
        "//java/com/google/domain/registry/xml",
        "//javatests/com/google/domain/registry/model",
        "//javatests/com/google/domain/registry/rde",
        "//javatests/com/google/domain/registry/testing",
        "//javatests/com/google/domain/registry/xml",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/jcommander",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/json_simple",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["**/*Test.java"]),
    deps = [":tools"],
)

