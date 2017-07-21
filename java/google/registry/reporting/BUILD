package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "reporting",
    srcs = glob(["*.java"]),
    resources = glob(["sql/*"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/gcs",
        "//java/google/registry/keyring/api",
        "//java/google/registry/model",
        "//java/google/registry/request",
        "//java/google/registry/request/auth",
        "//java/google/registry/util",
        "//java/google/registry/xjc",
        "//java/google/registry/xml",
        "@com_google_appengine_tools_appengine_gcs_client",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_http_client",
        "@javax_servlet_api",
        "@joda_time",
    ],
)
