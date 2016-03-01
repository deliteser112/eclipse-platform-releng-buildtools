package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/templates:closure_template_java_library.bzl", "closure_template_java_library")


closure_template_java_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    java_package = "com.google.domain.registry.tools.soy",
)
