package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "whitebox",
    srcs = glob(["*.java"]),
    deps = [
        "//apiserving/discoverydata/bigquery:bigqueryv2",
        "//java/com/google/api/client/http",
        "//java/com/google/api/client/json",
        "//java/com/google/api/client/util",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/common/testing",
        "//java/com/google/domain/registry/bigquery",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/monitoring/whitebox",
        "//java/com/google/domain/registry/util",
        "//javatests/com/google/domain/registry/testing",
        "//javatests/com/google/domain/registry/testing/mapreduce",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine:appengine-stubs",
        "//third_party/java/appengine:appengine-testing",
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
    deps = [":whitebox"],
)
