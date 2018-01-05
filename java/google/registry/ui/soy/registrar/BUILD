package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_java_template_library", "closure_js_template_library")

closure_js_template_library(
    name = "registrar",
    srcs = glob(["*.soy"]),
    data = ["//java/google/registry/ui/css:registrar_raw"],
    globals = "//java/google/registry/ui:globals.txt",
    # TODO: remove this when upgrading rules closure.  This is only necessary because
    # our version of soy is newer than what rules closure expects
    should_generate_js_doc = False,
    deps = ["//java/google/registry/ui/soy"],
)

closure_java_template_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    java_package = "google.registry.ui.soy.registrar",
)
