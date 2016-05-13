package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


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
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/flows",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/util",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/dagger",
        "//third_party/java/joda_time",
        "//third_party/java/jsr330_inject",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
    ],
)
