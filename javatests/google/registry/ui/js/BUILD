package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//third_party/closure/compiler:closure_js_library.bzl", "closure_js_library")
load("//third_party/closure/testing:closure_js_test.bzl", "closure_js_test")


closure_js_library(
    name = "testing",
    srcs = ["testing.js"],
    deps = ["//javascript/closure"],
)

closure_js_test(
    name = "test",
    size = "medium",
    timeout = "short",
    srcs = glob(["*_test.js"]),
    deps = [
        ":testing",
        "//javascript/closure",
        "//java/google/registry/ui/js",
    ],
)
