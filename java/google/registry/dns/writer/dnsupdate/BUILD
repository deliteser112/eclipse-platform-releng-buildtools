package(
    default_visibility = ["//java/google/registry:registry_project"],
)


java_library(
    name = "dnsupdate",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/common/primitives",
        "//java/google/registry/config",
        "//java/google/registry/dns/writer/api",
        "//java/google/registry/model",
        "//java/google/registry/util",
        "//third_party/java/dagger",
        "//third_party/java/dnsjava",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
    ],
)
