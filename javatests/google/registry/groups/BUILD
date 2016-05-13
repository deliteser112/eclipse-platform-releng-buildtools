package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")


java_library(
    name = "groups",
    srcs = glob(["*.java"]),
    deps = [
        "//apiserving/discoverydata/admin:admin_directory_v1",
        "//apiserving/discoverydata/groupssettings",
        "//java/com/google/api/client/googleapis/json",
        "//java/com/google/api/client/http",
        "//java/com/google/api/client/json",
        "//java/com/google/api/client/json/jackson2",
        "//java/com/google/api/client/testing/http",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/cron",
        "//java/com/google/domain/registry/groups",
        "//java/com/google/domain/registry/model",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/appengine:appengine-api-testonly",
        "//third_party/java/appengine:appengine-stubs",
        "//third_party/java/appengine:appengine-testing",
        "//third_party/java/joda_time",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":groups"],
)
