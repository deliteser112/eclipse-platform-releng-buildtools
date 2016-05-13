package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "server",
    srcs = glob(["*.java"]),
    resources = [
        "//java/com/google/domain/registry/ui:globals.txt",
        "//java/com/google/domain/registry/ui/css:registrar_bin.css.js",
        "//java/com/google/domain/registry/ui/css:registrar_dbg.css.js",
    ],
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/security",
        "//java/com/google/domain/registry/ui",
        "//java/com/google/domain/registry/ui/forms",
        "//java/com/google/domain/registry/util",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/joda_time",
        "//third_party/java/json_simple",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/servlet/servlet_api",

        "//third_party/closure/templates",
    ],
)
