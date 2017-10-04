package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "params",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/model",
        "//java/google/registry/tools/params",
        "//javatests/google/registry/testing",
        "//third_party/java/truth",
        "@com_beust_jcommander",
        "@com_google_guava",
        "@joda_time",
        "@junit",
        "@org_hamcrest_library",
        "@org_joda_money",
    ],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":params"],
)
