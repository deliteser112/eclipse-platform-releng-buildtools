package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/templates:closure_template_java_library.bzl", "closure_template_java_library")
load("//third_party/closure/templates:closure_template_js_library.bzl", "closure_template_js_library")


filegroup(
    name = "js_files",
    srcs = ["DomainCheckFeeEpp.soy.js"],
)

closure_template_java_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    java_package = "com.google.domain.registry.ui.soy.api",
)

closure_template_js_library(
    name = "DomainCheckFeeEpp",
    srcs = ["DomainCheckFeeEpp.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
)
