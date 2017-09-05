package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "lock",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/model",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@joda_time",
    ],
)
