package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "export",
    srcs = glob(["*.java"]),
    resources = glob([
        "testdata/*",
        "backup_kinds.txt",
    ]),
    deps = [
        "//apiserving/discoverydata/bigquery:bigqueryv2",
        "//apiserving/discoverydata/drive",
        "//java/com/google/api/client/http",
        "//java/com/google/api/client/json",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/bigquery",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/export",
        "//java/com/google/domain/registry/gcs",
        "//java/com/google/domain/registry/groups",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/storage/drive",
        "//java/com/google/domain/registry/util",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine:appengine-stubs",
        "//third_party/java/appengine_gcs_client",
        "//third_party/java/dagger",
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
    deps = [":export"],
)
