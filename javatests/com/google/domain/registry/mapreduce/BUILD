package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "mapreduce",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/model",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine:appengine-testing",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/junit",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    default_test_size = "medium",
    jvm_flags = ["-XX:MaxPermSize=256m"],
    test_files = glob(["*Test.java"]),
    deps = [":mapreduce"],
)
