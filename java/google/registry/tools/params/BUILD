package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0


java_library(
    name = "params",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/common/primitives",
        "//third_party/java/jcommander",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//java/google/registry/model",
        "//java/google/registry/util",
    ],
)
