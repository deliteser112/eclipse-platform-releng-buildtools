package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "gcs",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/config",
        "//third_party/java/appengine_gcs_client",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_flogger",
        "@com_google_flogger_system_backend",
        "@com_google_guava",
    ],
)
