package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

java_library(
    name = "appengine_pipeline",
    exports = ["@appengine_pipeline//jar"],
    runtime_deps = [
        "@com_google_appengine_tools_appengine_gcs_client",
        "@guava//jar",
        "@javax_servlet_api",
        "@json//jar",
    ],
)
