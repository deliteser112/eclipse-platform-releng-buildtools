package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0


java_library(
    name = "writer",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//java/google/registry/model",
    ],
)
