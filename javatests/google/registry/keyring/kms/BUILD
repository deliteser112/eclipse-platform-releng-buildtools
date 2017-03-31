package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "kms",
    srcs = glob(["*.java"]),
    resources = [
        "pgp-private-keyring-registry.asc",
        "pgp-public-keyring.asc",
    ],
    deps = [
        "//java/google/registry/keyring/api",
        "//java/google/registry/keyring/kms",
        "//java/google/registry/model",
        "//javatests/google/registry/testing",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_api_client",
        "@com_google_apis_google_api_services_cloudkms",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
        "@com_google_http_client",
        "@com_google_http_client_jackson2",
        "@com_google_truth",
        "@junit",
        "@org_bouncycastle_bcpg_jdk15on",
        "@org_bouncycastle_bcpkix_jdk15on",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":kms"],
)
