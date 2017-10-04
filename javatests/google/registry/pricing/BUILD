package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "pricing",
    srcs = glob(["*Test.java"]),
    deps = [
        "//java/google/registry/model",
        "//java/google/registry/pricing",
        "//java/google/registry/util",
        "//javatests/google/registry/testing",
        "//third_party/java/truth",
        "@com_google_guava",
        "@joda_time",
        "@junit",
        "@org_joda_money",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":pricing"],
)
