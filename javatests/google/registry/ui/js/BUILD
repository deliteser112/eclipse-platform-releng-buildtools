package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

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
        "//java/com/google/domain/registry/ui/js",
        "//javascript/closure",
    ],
)
