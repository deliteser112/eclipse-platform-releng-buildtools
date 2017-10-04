package(
    default_testonly = 1,
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "contrib",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/monitoring/metrics",
        "//third_party/java/truth",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
    ],
)
