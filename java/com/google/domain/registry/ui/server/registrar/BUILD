package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "registrar",
    srcs = glob(["*.java"]),
    resources = [
        "//java/com/google/domain/registry/ui/css:registrar_bin.css.js",
        "//java/com/google/domain/registry/ui/css:registrar_dbg.css.js",
    ],
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/export/sheet",
        "//java/com/google/domain/registry/flows",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/security",
        "//java/com/google/domain/registry/security:servlets",
        "//java/com/google/domain/registry/ui/forms",
        "//java/com/google/domain/registry/ui/server",
        "//java/com/google/domain/registry/ui/soy:soy_java_wrappers",
        "//java/com/google/domain/registry/ui/soy/registrar:soy_java_wrappers",
        "//java/com/google/domain/registry/util",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/braintree",
        "//third_party/java/dagger",
        "//third_party/java/joda_money",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",

        "//third_party/closure/templates",
    ],
    visibility = ["//visibility:public"],
)
