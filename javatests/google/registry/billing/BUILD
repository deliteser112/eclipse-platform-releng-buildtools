package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "billing",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/junit",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/truth",
        "//java/google/registry/billing",
        "//java/google/registry/mapreduce",
        "//java/google/registry/model",
        "//java/google/registry/util",
        "//javatests/google/registry/model",
        "//javatests/google/registry/testing",
        "//javatests/google/registry/testing/mapreduce",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    default_test_size = "medium",
    shard_count = 3,
    test_files = glob(["*Test.java"]),
    deps = [":billing"],
)
