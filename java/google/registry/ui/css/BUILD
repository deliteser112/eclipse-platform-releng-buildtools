package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_css_library", "closure_css_binary")


filegroup(
    name = "css_files",
    srcs = glob(["*.css"]),
    compatible_with = ["//buildenv/target:appengine"],
)

closure_css_library(
    name = "kd_components_lib",
    srcs = ["kd_components.css"],
    compatible_with = ["//buildenv/target:appengine"],
)


closure_css_library(
    name = "registry_lib",
    srcs = [
        "console.css",
        "forms.css",
        "registry.css",
    ],
    compatible_with = ["//buildenv/target:appengine"],
    deps = [":kd_components_lib"],
)

closure_css_library(
    name = "registrar_lib",
    srcs = [
        "contact-settings.css",
        "contact-us.css",
        "dashboard.css",
        "epp.css",
        "resources.css",
        "security-settings.css",
    ],
    compatible_with = ["//buildenv/target:appengine"],
    deps = [":registry_lib"],
)

closure_css_binary(
    name = "registrar_bin",
    compatible_with = ["//buildenv/target:appengine"],
    deps = [":registrar_lib"],
)

closure_css_binary(
    name = "registrar_dbg",
    compatible_with = ["//buildenv/target:appengine"],
    debug = 1,
    deps = [":registrar_lib"],
)
