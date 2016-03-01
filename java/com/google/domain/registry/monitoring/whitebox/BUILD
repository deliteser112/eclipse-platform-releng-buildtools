package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "whitebox",
    srcs = glob(["*.java"]),
    deps = [
        "//apiserving/discoverydata/bigquery:bigqueryv2",
        "//java/com/google/api/client/extensions/appengine/http",
        "//java/com/google/api/client/googleapis/extensions/appengine/auth/oauth2",
        "//java/com/google/api/client/json/jackson2",
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/cache",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/bigquery",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/util",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
    ],
)
