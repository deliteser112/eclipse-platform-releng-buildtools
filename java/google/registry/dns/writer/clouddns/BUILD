package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "clouddns",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/dns/writer",
        "//java/google/registry/model",
        "//java/google/registry/util",
        "@com_google_api_client",
        "@com_google_apis_google_api_services_dns",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_http_client",
        "@joda_time",
    ],
)
