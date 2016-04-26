package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0


java_library(
    name = "api",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/google/registry/config",
        "//java/google/registry/flows",
        "//java/google/registry/model",
        "//java/google/registry/ui/server",
        "//java/google/registry/ui/soy/api:soy_java_wrappers",
        "//java/google/registry/util",
        "//third_party/java/json_simple",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/servlet/servlet_api",

        "//third_party/closure/templates",
    ],
)
