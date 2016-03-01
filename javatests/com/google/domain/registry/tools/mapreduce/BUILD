package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "mapreduce",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/tools/mapreduce",
        "//java/com/google/domain/registry/util",
        "//javatests/com/google/domain/registry/testing",
        "//javatests/com/google/domain/registry/testing/mapreduce",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/appengine_pipeline",
        "//third_party/java/hamcrest",
        "//third_party/java/jcommander",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",
    ],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":mapreduce"],
)
