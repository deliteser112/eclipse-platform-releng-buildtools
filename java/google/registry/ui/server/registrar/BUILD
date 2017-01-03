package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "registrar",
    srcs = glob(["*.java"]),
    resources = [
        "//java/google/registry/ui/css:registrar_bin.css.js",
        "//java/google/registry/ui/css:registrar_dbg.css.js",
    ],
    deps = [
        "//java/google/registry/braintree",
        "//java/google/registry/config",
        "//java/google/registry/export/sheet",
        "//java/google/registry/flows",
        "//java/google/registry/model",
        "//java/google/registry/request",
        "//java/google/registry/security",
        "//java/google/registry/ui/forms",
        "//java/google/registry/ui/server",
        "//java/google/registry/ui/soy:soy_java_wrappers",
        "//java/google/registry/ui/soy/registrar:soy_java_wrappers",
        "//java/google/registry/util",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_braintreepayments_gateway_braintree_java",
        "@com_google_appengine_api_1_0_sdk",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_re2j",
        "@io_bazel_rules_closure//closure/templates",
        "@javax_servlet_api",
        "@org_joda_money",
    ],
)
