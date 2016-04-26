package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0

load("//third_party/closure/templates:closure_template_java_library.bzl", "closure_template_java_library")
load("//third_party/closure/templates:closure_template_js_library.bzl", "closure_template_js_library")


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
