package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/compiler:closure_js_library.bzl", "closure_js_library")
load("//third_party/closure/testing:closure_js_test.bzl", "closure_js_test")


closure_js_library(
    name = "console_test_util",
    srcs = ["console_test_util.js"],
    deps = [
        "//java/com/google/domain/registry/ui/js",
        "//java/com/google/domain/registry/ui/js/registrar",
        "//javascript/closure",
    ],
)

closure_js_test(
    name = "test",
    size = "medium",
    timeout = "short",
    srcs = glob(["*_test.js"]),
    deps = [
        ":console_test_util",
        "//java/com/google/domain/registry/ui/js",
        "//java/com/google/domain/registry/ui/js/registrar",
        "//java/com/google/domain/registry/ui/soy/registrar:Console",
        "//javascript/closure",
        "//javatests/com/google/domain/registry/ui/js:testing",
    ],
)
