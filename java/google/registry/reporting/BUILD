package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "reporting",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/request",
        "//java/google/registry/util",
        "@com_google_dagger",
        "@javax_servlet_api",
        "@joda_time",
    ],
)
