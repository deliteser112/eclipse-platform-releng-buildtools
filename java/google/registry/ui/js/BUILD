package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")


filegroup(
    name = "js_files",
    srcs = glob(["*.js"]),
    compatible_with = ["//buildenv/target:appengine"],
)

closure_js_library(
    name = "js",
    srcs = [":js_files"],
    compatible_with = ["//buildenv/target:appengine"],
    deps = [
        "//javascript/closure",
        "//java/google/registry/ui/soy:Console",
        "//java/google/registry/ui/soy:Forms",
    ],
)
