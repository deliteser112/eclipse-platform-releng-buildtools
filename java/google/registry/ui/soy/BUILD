package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_template_java_library", "closure_template_js_library")


filegroup(
    name = "js_files",
    srcs = [
        "Console.soy.js",
        "Forms.soy.js",
    ],
)

closure_template_java_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    java_package = "google.registry.ui.soy",
)

closure_template_js_library(
    name = "Console",
    srcs = ["Console.soy"],
    globals = "//java/google/registry/ui:globals.txt",
)

closure_template_js_library(
    name = "Forms",
    srcs = ["Forms.soy"],
    globals = "//java/google/registry/ui:globals.txt",
)
