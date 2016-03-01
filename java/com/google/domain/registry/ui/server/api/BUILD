package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "api",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/flows",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/ui/server",
        "//java/com/google/domain/registry/ui/soy/api:soy_java_wrappers",
        "//java/com/google/domain/registry/util",
        "//third_party/java/json_simple",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/servlet/servlet_api",

        "//third_party/closure/templates",
    ],
)
