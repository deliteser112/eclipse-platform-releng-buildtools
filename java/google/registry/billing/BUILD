package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "billing",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/request",
        "//java/google/registry/request/auth",
        "@com_google_dagger",
        "@com_google_guava",
        "@joda_time",
        "@org_apache_beam_runners_google_cloud_dataflow_java",
        "@org_apache_beam_sdks_java_core",
    ],
)

java_binary(
    name = "counter",
    srcs = ["MinWordCount.java"],
    main_class = "google.registry.billing.MinWordCount",
    deps = [
        "@com_google_dagger",
        "@com_google_guava",
        "@joda_time",
        "@org_apache_beam_runners_google_cloud_dataflow_java",
        "@org_apache_beam_sdks_java_core",
    ],
)
