package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "request",
    srcs = glob(
        ["*.java"],
        exclude = ["Modules.java"],
    ),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/html",
        "//java/com/google/common/io",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/security",
        "//java/com/google/domain/registry/util",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/auto:auto_value",
        "//third_party/java/dagger",
        "//third_party/java/joda_time",
        "//third_party/java/json_simple",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)

java_library(
    name = "modules",
    srcs = ["Modules.java"],
    deps = [
        ":request",
        "//java/com/google/api/client/extensions/appengine/http",
        "//java/com/google/api/client/googleapis/auth/oauth2",
        "//java/com/google/api/client/googleapis/extensions/appengine/auth/oauth2",
        "//java/com/google/api/client/http",
        "//java/com/google/api/client/json",
        "//java/com/google/api/client/json/jackson2",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/keyring/api",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/dagger",
    ],
)
