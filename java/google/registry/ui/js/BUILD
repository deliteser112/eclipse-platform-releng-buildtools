package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/compiler:closure_js_library.bzl", "closure_js_library")


filegroup(
    name = "js_files",
    srcs = glob(["*.js"]),
)

closure_js_library(
    name = "js",
    srcs = [":js_files"],
    deps = [
        "//java/com/google/domain/registry/ui/soy:Console",
        "//java/com/google/domain/registry/ui/soy:Forms",
        "//javascript/closure",
    ],
)
