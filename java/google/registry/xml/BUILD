package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0


filegroup(
    name = "xml_schema_files",
    srcs = glob(["xsd/*.xsd"]),
    compatible_with = ["//buildenv/target:appengine"],
)

java_library(
    name = "xml",
    srcs = glob(["*.java"]),
    compatible_with = ["//buildenv/target:appengine"],
    resources = [":xml_schema_files"],
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/re2j",
    ],
)
