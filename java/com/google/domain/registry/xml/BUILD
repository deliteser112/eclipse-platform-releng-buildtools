package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


filegroup(
    name = "xml_schema_files",
    srcs = glob(["xsd/*.xsd"]),
)

java_library(
    name = "xml",
    srcs = glob(["*.java"]),
    resources = [":xml_schema_files"],
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
    ],
)
