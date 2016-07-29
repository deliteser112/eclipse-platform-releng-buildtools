package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0


filegroup(
    name = "templates",
    srcs = glob(["templates/*.xml"]),
)

java_library(
    name = "loadtest",
    srcs = glob(["*.java"]),
    resources = [":templates"],
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/dagger",
        "//third_party/java/joda_time",
        "//third_party/java/jsr330_inject",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
        "//java/google/registry/config",
        "//java/google/registry/request",
        "//java/google/registry/util",
    ],
)
