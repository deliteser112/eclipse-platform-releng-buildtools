package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "sheet",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/api/client/googleapis/auth/oauth2",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/model",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/util",
        "//java/com/google/gdata",
        "//java/com/google/gdata:spreadsheet",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/dagger",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)
