package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_java_template_library", "closure_js_template_library")


filegroup(
    name = "js_files",
    srcs = [
        "Console.soy.js",
        "Forms.soy.js",
    ],
    compatible_with = ["//buildenv/target:appengine"],
)

closure_java_template_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    compatible_with = ["//buildenv/target:appengine"],
    java_package = "google.registry.ui.soy",
)

closure_js_template_library(
    name = "Console",
    srcs = ["Console.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
)

closure_js_template_library(
    name = "Forms",
    srcs = ["Forms.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
)
