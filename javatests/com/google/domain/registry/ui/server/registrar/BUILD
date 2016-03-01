package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "registrar",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/testing",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/export/sheet",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/security",
        "//java/com/google/domain/registry/ui/server/registrar",
        "//java/com/google/domain/registry/ui/soy/registrar:soy_java_wrappers",
        "//java/com/google/domain/registry/util",
        "//javatests/com/google/domain/registry/security",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/braintree",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/json_simple",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",

        "//third_party/closure/templates",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":registrar"],
)
