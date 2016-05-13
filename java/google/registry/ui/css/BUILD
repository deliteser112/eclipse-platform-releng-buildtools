package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/stylesheets:closure_css_library.bzl", "closure_css_library")
load("//third_party/closure/stylesheets:closure_css_binary.bzl", "closure_css_binary")


filegroup(
    name = "css_files",
    srcs = glob(["*.css"]),
)

closure_css_library(
    name = "kd_components_lib",
    srcs = ["kd_components.css"],
)


closure_css_library(
    name = "registry_lib",
    srcs = [
        "console.css",
        "forms.css",
        "registry.css",
    ],
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
    deps = [":registry_lib"],
)

closure_css_binary(
    name = "registrar_bin",
    deps = [":registrar_lib"],
)

closure_css_binary(
    name = "registrar_dbg",
    debug = 1,
    deps = [":registrar_lib"],
)
