package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "server",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/groups",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/tools/server",
        "//java/com/google/domain/registry/util",
        "//javatests/com/google/domain/registry/model",
        "//javatests/com/google/domain/registry/testing",
        "//javatests/com/google/domain/registry/testing/mapreduce",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine_gcs_client",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":server"],
)
