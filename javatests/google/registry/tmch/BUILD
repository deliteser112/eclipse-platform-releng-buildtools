package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "tmch",
    srcs = glob(["*.java"]),
    resources = glob([
        "*.crt",
        "testdata/*",
        "testdata/*/*",
        "testdata/*/*/*",
    ]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/tmch",
        "//java/com/google/domain/registry/util",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/bouncycastle",
        "//third_party/java/bouncycastle_bcpg",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":tmch"],
)
