package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/testing:closure_js_test.bzl", "closure_js_test")


closure_js_test(
    name = "test",
    size = "medium",
    timeout = "short",
    srcs = glob(["*_test.js"]),
    deps = [
        "//java/com/google/domain/registry/ui/js/admin",
        "//java/com/google/domain/registry/ui/soy/admin:Console",
        "//javascript/closure",
        "//javatests/com/google/domain/registry/ui/js:testing",
    ],
)
