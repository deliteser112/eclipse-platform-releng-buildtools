package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "idn",
    srcs = glob(["*.java"]),
    resources = glob(["*.txt"]),
    deps = [
        "//java/google/registry/util",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
        "@com_google_re2j",
        "@javax_servlet_api",
        "@joda_time",
    ],
)
