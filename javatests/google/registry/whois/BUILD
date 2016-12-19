package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "whois",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*.txt"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/model",
        "//java/google/registry/request",
        "//java/google/registry/util",
        "//java/google/registry/whois",
        "//javatests/google/registry/testing",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_appengine_testing",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_truth",
        "@javax_servlet_api",
        "@joda_time",
        "@junit",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":whois"],
)
