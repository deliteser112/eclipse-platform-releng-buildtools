package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/templates:closure_template_java_library.bzl", "closure_template_java_library")
load("//third_party/closure/templates:closure_template_js_library.bzl", "closure_template_js_library")


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
)

closure_template_java_library(
    name = "soy_java_wrappers",
    srcs = glob(["*.soy"]),
    java_package = "com.google.domain.registry.ui.soy.registrar",
)

closure_template_js_library(
    name = "Console",
    srcs = ["Console.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Console"],
)

closure_template_js_library(
    name = "ContactEpp",
    srcs = ["ContactEpp.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
)

closure_template_js_library(
    name = "ContactSettings",
    srcs = ["ContactSettings.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Forms"],
)

closure_template_js_library(
    name = "Contact",
    srcs = ["Contact.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Forms"],
)

closure_template_js_library(
    name = "Domain",
    srcs = ["Domain.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Forms"],
)

closure_template_js_library(
    name = "DomainEpp",
    srcs = ["DomainEpp.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
)

closure_template_js_library(
    name = "Epp",
    srcs = ["Epp.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
)

closure_template_js_library(
    name = "HostEpp",
    srcs = ["HostEpp.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
)

closure_template_js_library(
    name = "Host",
    srcs = ["Host.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Forms"],
)

closure_template_js_library(
    name = "Payment",
    srcs = ["Payment.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Forms"],
)

closure_template_js_library(
    name = "SecuritySettings",
    srcs = ["SecuritySettings.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
)

closure_template_js_library(
    name = "WhoisSettings",
    srcs = ["WhoisSettings.soy"],
    globals = "//java/com/google/domain/registry/ui:globals.txt",
    deps = ["//java/com/google/domain/registry/ui/soy:Forms"],
)
