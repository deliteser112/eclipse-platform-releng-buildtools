package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")

closure_js_library(
    name = "closure",
    exports = ["@io_bazel_rules_closure//closure/library"],
)

closure_js_library(
    name = "testing",
    testonly = 1,
    exports = ["@io_bazel_rules_closure//closure/library:testing"],
)

filegroup(
    name = "js_files_recursive",
    srcs = ["@closure_library//:js_files"],
)
