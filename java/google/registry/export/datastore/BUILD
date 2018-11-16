package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "datastore",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/config",
        "@com_google_api_client",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_http_client",
        "@com_google_http_client_jackson2",
    ],
)
