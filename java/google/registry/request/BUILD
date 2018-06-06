package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "request",
    srcs = glob(
        ["*.java"],
        exclude = ["Modules.java"],
    ),
    deps = [
        "//java/google/registry/request/auth",
        "//java/google/registry/request/lock",
        "//java/google/registry/util",
        "//third_party/java/appengine:appengine-api",
        "@com_google_auto_value",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_flogger",
        "@com_google_flogger_system_backend",
        "@com_google_guava",
        "@com_googlecode_json_simple",
        "@javax_servlet_api",
        "@joda_time",
    ],
)

java_library(
    name = "modules",
    srcs = ["Modules.java"],
    deps = [
        "//java/com/google/api/client/extensions/appengine/http",
        "//java/com/google/api/client/googleapis/extensions/appengine/auth/oauth2",
        "//java/google/registry/config",
        "//java/google/registry/keyring/api",
        "//third_party/java/appengine:appengine-api",
        "@com_google_api_client",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_http_client",
        "@com_google_http_client_jackson2",
    ],
)
