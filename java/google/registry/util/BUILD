package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "util",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/config",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_appengine_api_1_0_sdk",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_re2j",
        "@com_ibm_icu_icu4j",
        "@javax_servlet_api",
        "@joda_time",
    ],
)
