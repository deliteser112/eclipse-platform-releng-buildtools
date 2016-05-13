package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "idn",
    srcs = glob(["*.java"]),
    resources = glob(["*.txt"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/domain/registry/util",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
    ],
)
