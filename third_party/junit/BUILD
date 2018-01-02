package(
    default_visibility = ["//visibility:public"],
)

licenses(["reciprocal"])  # EPL 1.0

exports_files(["LICENSE"])

java_library(
    name = "junit",
    srcs = glob(["**/*.java"]),
    visibility = [
        "//javatests/google/registry/testing:__pkg__",
    ],
)
