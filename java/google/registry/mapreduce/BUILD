package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "mapreduce",
    srcs = glob(["*.java"]),
    deps = [
        "//java/google/registry/mapreduce/inputs",
        "//java/google/registry/request",
        "//java/google/registry/util",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/appengine_pipeline",
        "//third_party/objectify:objectify-v4_1",
        "@com_google_dagger",
        "@com_google_flogger",
        "@com_google_flogger_system_backend",
        "@com_google_guava",
        "@javax_servlet_api",
        "@joda_time",
    ],
)
