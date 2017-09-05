package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "api",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/keyring/api",
        "//javatests/google/registry/testing",
        "@com_google_guava_testlib",
        "@com_google_truth",
        "@junit",
        "@org_bouncycastle_bcpg_jdk15on",
        "@org_bouncycastle_bcpkix_jdk15on",
        "@org_hamcrest_library",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":api"],
)
