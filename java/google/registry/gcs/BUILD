package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0


java_library(
    name = "gcs",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//third_party/java/appengine_gcs_client",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//java/google/registry/config",
        "//java/google/registry/util",
    ],
)
