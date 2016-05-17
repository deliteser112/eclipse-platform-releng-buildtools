package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_template_java_library", "closure_template_js_library")


filegroup(
    name = "js_files",
    srcs = ["DomainCheckFeeEpp.soy.js"],
)

closure_template_java_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    java_package = "google.registry.ui.soy.api",
)

closure_template_js_library(
    name = "DomainCheckFeeEpp",
    srcs = ["DomainCheckFeeEpp.soy"],
    globals = "//java/google/registry/ui:globals.txt",
)
