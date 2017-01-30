package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

java_binary(
    name = "SheepCounterExample",
    srcs = ["SheepCounterExample.java"],
    deps = [
        "//java/google/registry/monitoring/metrics",
        "//java/google/registry/monitoring/metrics/stackdriver",
        "@com_google_api_client",
        "@com_google_apis_google_api_services_monitoring",
        "@com_google_guava",
        "@com_google_http_client",
        "@com_google_http_client_jackson2",
    ],
)
