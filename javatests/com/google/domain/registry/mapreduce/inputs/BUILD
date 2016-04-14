package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "inputs",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/mapreduce/inputs",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/util",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine:appengine-testing",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
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
    deps = [":inputs"],
)
