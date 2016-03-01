package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "rde",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/gcs",
        "//java/com/google/domain/registry/keyring/api",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/rde",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/tldconfig/idn",
        "//java/com/google/domain/registry/util",
        "//java/com/google/domain/registry/xjc",
        "//java/com/google/domain/registry/xml",
        "//javatests/com/google/domain/registry/testing",
        "//javatests/com/google/domain/registry/testing/mapreduce",
        "//javatests/com/google/domain/registry/testing/sftp",
        "//javatests/com/google/domain/registry/xml",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine_gcs_client",
        "//third_party/java/bouncycastle",
        "//third_party/java/bouncycastle_bcpg",
        "//third_party/java/dagger",
        "//third_party/java/ftpserver",
        "//third_party/java/hamcrest",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    default_test_size = "medium",
    shard_count = 3,
    test_files = glob(["*Test.java"]),
    deps = [":rde"],
)
