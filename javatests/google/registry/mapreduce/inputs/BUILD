package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "inputs",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/mapreduce/inputs",
        "//java/google/registry/model",
        "//java/google/registry/util",
        "//javatests/google/registry/testing",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_testing",
        "@com_google_appengine_tools_appengine_mapreduce",
        "@com_google_guava",
        "@com_google_truth",
        "@joda_time",
        "@junit",
        "@org_joda_money",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    default_test_size = "medium",
    jvm_flags = ["-XX:MaxPermSize=256m"],
    test_files = glob(["*Test.java"]),
    deps = [":inputs"],
)
