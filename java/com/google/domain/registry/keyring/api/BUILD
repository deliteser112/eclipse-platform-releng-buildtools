package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "api",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/common/base",
        "//third_party/java/bouncycastle",
        "//third_party/java/bouncycastle_bcpg",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
    ],
)
