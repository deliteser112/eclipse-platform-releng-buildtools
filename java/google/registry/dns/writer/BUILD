package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "writer",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/logging:formatting_logger",
        "@com_google_dagger",
        "@com_google_guava",
    ],
)
