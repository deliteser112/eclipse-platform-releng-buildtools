package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

load("//third_party/closure/compiler:closure_js_check_test.bzl", "closure_js_check_test")

exports_files([
    "phantomjs_jsunit_runner.js",
    "phantomjs_runner.js",
])

closure_js_check_test(
    name = "phantomjs_jsunit_runner_check",
    srcs = ["phantomjs_jsunit_runner.js"],
    pedantic = 1,
    visibility = ["//visibility:private"],
    deps = ["//third_party/closure/library"],
)

closure_js_check_test(
    name = "phantomjs_runner_check",
    srcs = ["phantomjs_runner.js"],
    externs_list = ["externs/phantom.js"],
    pedantic = 1,
    visibility = ["//visibility:private"],
)
