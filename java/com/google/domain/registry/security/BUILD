package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "security",
    srcs = glob(
        ["*.java"],
        exclude = glob(["*Servlet.java"]),
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/hash",
        "//java/com/google/common/html",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/util",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/joda_time",
        "//third_party/java/json_simple",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/servlet/servlet_api",
    ],
)

java_library(
    name = "servlets",
    srcs = glob(["*Servlet.java"]),
    deps = [
        ":security",
        "//java/com/google/common/base",
        "//java/com/google/domain/registry/request",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/joda_time",
        "//third_party/java/servlet/servlet_api",
    ],
)
