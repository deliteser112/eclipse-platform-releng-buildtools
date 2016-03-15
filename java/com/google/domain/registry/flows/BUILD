package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


filegroup(
    name = "flows_files",
    srcs = glob([
        "*.java",
        "**/*.java",
    ]),
)

java_library(
    name = "flows",
    srcs = glob([
        "*.java",
        "**/*.java",
    ]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/dns",
        "//java/com/google/domain/registry/mapreduce",
        "//java/com/google/domain/registry/mapreduce/inputs",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/monitoring/whitebox",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/security:servlets",
        "//java/com/google/domain/registry/tldconfig/idn",
        "//java/com/google/domain/registry/tmch",
        "//java/com/google/domain/registry/util",
        "//java/com/google/domain/registry/xml",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/dagger",
        "//third_party/java/joda_money",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
    ],
)
