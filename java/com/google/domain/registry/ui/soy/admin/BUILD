package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/templates:closure_template_java_library.bzl", "closure_template_java_library")
load("//third_party/closure/templates:closure_template_js_library.bzl", "closure_template_js_library")


filegroup(
    name = "js_files",
    srcs = [
        "Console.soy.js",
        "Registrar.soy.js",
        "Registry.soy.js",
    ],
)

closure_template_java_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    java_package = "com.google.domain.registry.ui.soy.admin",
)

closure_template_js_library(
    name = "Console",
    srcs = ["Console.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Console"],
)

closure_template_js_library(
    name = "Registrar",
    srcs = ["Registrar.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Forms"],
)

closure_template_js_library(
    name = "Registry",
    srcs = ["Registry.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Forms"],
)
