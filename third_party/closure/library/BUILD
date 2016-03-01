package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

load("//third_party/closure/compiler:closure_js_library.bzl", "closure_js_library")
load("//third_party/closure/stylesheets:closure_css_library.bzl", "closure_css_library")

closure_js_library(
    name = "library",
    srcs = ["@closure_library//:js_files"],
)

closure_css_library(
    name = "css",
    srcs = ["@closure_library//:css_files"],
)
