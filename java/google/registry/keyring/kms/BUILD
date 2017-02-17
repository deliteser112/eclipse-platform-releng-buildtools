package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "kms",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/keyring/api",
        "//java/google/registry/model",
        "//java/google/registry/util",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_api_client",
        "@com_google_apis_google_api_services_cloudkms",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_http_client",
        "@org_bouncycastle_bcpg_jdk15on",
        "@org_bouncycastle_bcpkix_jdk15on",
    ],
)
