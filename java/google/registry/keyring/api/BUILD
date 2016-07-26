package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0


java_library(
    name = "api",
    srcs = glob(["*.java"]),
    compatible_with = ["//buildenv/target:appengine"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/common/base",
        "//third_party/java/bouncycastle",
        "//third_party/java/bouncycastle_bcpg",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
    ],
)
