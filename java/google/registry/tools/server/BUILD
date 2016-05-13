package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "server",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/common/primitives",
        "//java/com/google/common/util/concurrent",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/export",
        "//java/com/google/domain/registry/flows",
        "//java/com/google/domain/registry/gcs",
        "//java/com/google/domain/registry/groups",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/mapreduce/inputs",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/util",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/appengine_gcs_client",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/dagger",
        "//third_party/java/jcommander",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
    ],
)
