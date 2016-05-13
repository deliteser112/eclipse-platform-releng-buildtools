package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/compiler:closure_js_library.bzl", "closure_js_library")


filegroup(
    name = "js_files",
    srcs = glob(["*.js"]),
)

closure_js_library(
    name = "registrar",
    srcs = [":js_files"],
    deps = [
        "//java/com/google/domain/registry/ui/js",
        "//java/com/google/domain/registry/ui/soy:Forms",
        "//java/com/google/domain/registry/ui/soy/registrar:Console",
        "//java/com/google/domain/registry/ui/soy/registrar:Contact",
        "//java/com/google/domain/registry/ui/soy/registrar:ContactEpp",
        "//java/com/google/domain/registry/ui/soy/registrar:ContactSettings",
        "//java/com/google/domain/registry/ui/soy/registrar:Domain",
        "//java/com/google/domain/registry/ui/soy/registrar:DomainEpp",
        "//java/com/google/domain/registry/ui/soy/registrar:Epp",
        "//java/com/google/domain/registry/ui/soy/registrar:Host",
        "//java/com/google/domain/registry/ui/soy/registrar:HostEpp",
        "//java/com/google/domain/registry/ui/soy/registrar:Payment",
        "//java/com/google/domain/registry/ui/soy/registrar:SecuritySettings",
        "//java/com/google/domain/registry/ui/soy/registrar:WhoisSettings",
        "//javascript/closure",
    ],
)
