package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_java_template_library", "closure_js_template_library")


filegroup(
    name = "js_files",
    srcs = [
        "Console.soy.js",
        "Contact.soy.js",
        "ContactEpp.soy.js",
        "ContactSettings.soy.js",
        "Domain.soy.js",
        "DomainEpp.soy.js",
        "Epp.soy.js",
        "Host.soy.js",
        "HostEpp.soy.js",
        "Payment.soy.js",
        "SecuritySettings.soy.js",
        "WhoisSettings.soy.js",
    ],
    compatible_with = ["//buildenv/target:appengine"],
)

closure_java_template_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    compatible_with = ["//buildenv/target:appengine"],
    java_package = "google.registry.ui.soy.registrar",
)

closure_js_template_library(
    name = "Console",
    srcs = ["Console.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
    deps = ["//java/google/registry/ui/soy:Console"],
)

closure_js_template_library(
    name = "ContactEpp",
    srcs = ["ContactEpp.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
)

closure_js_template_library(
    name = "ContactSettings",
    srcs = ["ContactSettings.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
    deps = ["//java/google/registry/ui/soy:Forms"],
)

closure_js_template_library(
    name = "Contact",
    srcs = ["Contact.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
    deps = ["//java/google/registry/ui/soy:Forms"],
)

closure_js_template_library(
    name = "Domain",
    srcs = ["Domain.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
    deps = ["//java/google/registry/ui/soy:Forms"],
)

closure_js_template_library(
    name = "DomainEpp",
    srcs = ["DomainEpp.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
)

closure_js_template_library(
    name = "Epp",
    srcs = ["Epp.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
)

closure_js_template_library(
    name = "HostEpp",
    srcs = ["HostEpp.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
)

closure_js_template_library(
    name = "Host",
    srcs = ["Host.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
    deps = ["//java/google/registry/ui/soy:Forms"],
)

closure_js_template_library(
    name = "Payment",
    srcs = ["Payment.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
    deps = ["//java/google/registry/ui/soy:Forms"],
)

closure_js_template_library(
    name = "SecuritySettings",
    srcs = ["SecuritySettings.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
)

closure_js_template_library(
    name = "WhoisSettings",
    srcs = ["WhoisSettings.soy"],
    compatible_with = ["//buildenv/target:appengine"],
    globals = "//java/google/registry/ui:globals.txt",
    deps = ["//java/google/registry/ui/soy:Forms"],
)
