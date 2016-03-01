package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

load("//third_party/closure/compiler:closure_js_library.bzl", "closure_js_library")

closure_js_library(
    name = "closure",
    exports = ["//third_party/closure/library"],
)

filegroup(
    name = "js_files_recursive",
    srcs = ["@closure_library//:js_files"],
)
