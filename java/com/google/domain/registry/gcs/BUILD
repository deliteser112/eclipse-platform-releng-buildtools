package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "gcs",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/util",
        "//third_party/java/appengine_gcs_client",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
    ],
)
