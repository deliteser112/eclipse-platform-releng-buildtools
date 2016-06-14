package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0


java_library(
    name = "billing",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/dagger",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
        "//java/google/registry/mapreduce",
        "//java/google/registry/mapreduce/inputs",
        "//java/google/registry/model",
        "//java/google/registry/pricing",
        "//java/google/registry/request",
        "//java/google/registry/util",
    ],
)
