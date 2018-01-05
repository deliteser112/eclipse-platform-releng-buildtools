package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_java_template_library", "closure_js_template_library")

closure_js_template_library(
    name = "soy",
    srcs = glob(["*.soy"]),
    globals = "//java/google/registry/ui:globals.txt",
    # TODO: remove this when upgrading rules closure.  This is only necessary because
    # our version of soy is newer than what rules closure expects
    should_generate_js_doc = False,
)

closure_java_template_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    java_package = "google.registry.ui.soy",
)
