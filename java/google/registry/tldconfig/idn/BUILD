package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0


java_library(
    name = "idn",
    srcs = glob(["*.java"]),
    compatible_with = ["//buildenv/target:appengine"],
    resources = glob(["*.txt"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/re2j",
        "//third_party/java/servlet/servlet_api",
        "//java/google/registry/util",
    ],
)
