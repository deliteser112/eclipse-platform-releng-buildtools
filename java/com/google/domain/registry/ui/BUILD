package(default_visibility = ["//java/com/google/domain/registry:registry_project"])

load("//third_party/closure/compiler:closure_js_binary.bzl", "closure_js_binary")
load("//third_party/closure/compiler:closure_js_deps.bzl", "closure_js_deps")

exports_files(["globals.txt"])


filegroup(
    name = "assets_recursive",
    srcs = glob(["assets/**"]),
)

filegroup(
    name = "js_files_recursive",
    srcs = [
        "//java/com/google/domain/registry/ui/js:js_files",
        "//java/com/google/domain/registry/ui/js/admin:js_files",
        "//java/com/google/domain/registry/ui/js/registrar:js_files",
        "//java/com/google/domain/registry/ui/soy:js_files",
        "//java/com/google/domain/registry/ui/soy/admin:js_files",
        "//java/com/google/domain/registry/ui/soy/api:js_files",
        "//java/com/google/domain/registry/ui/soy/registrar:js_files",
    ],
)

filegroup(
    name = "runfiles",
    srcs = [
        "admin_bin.js",
        "admin_bin_map.js",
        "brain_bin.js",
        "brain_bin_map.js",
        "registrar_bin.js",
        "registrar_bin_map.js",
        ":assets_recursive",
        "//java/com/google/domain/registry/ui:deps-runfiles.js",
        "//java/com/google/domain/registry/ui/css:admin_bin.css",
        "//java/com/google/domain/registry/ui/css:registrar_bin.css",
        "//java/com/google/domain/registry/ui/html:html_files",
        "//javascript/closure:js_files_recursive",
        "//javascript/template/soy:soy_usegoog_js_files",
    ],
)

filegroup(
    name = "runfiles_debug",
    srcs = [
        "deps-runfiles.js",
        ":js_files_recursive",
        ":runfiles",
        "//java/com/google/domain/registry/ui/css:css_files",
        "//javascript/closure:js_files_recursive",
        "//javascript/template/soy:soy_usegoog_js_files",
        "//third_party/javascript/closure:js_files_recursive",
    ],
)

java_library(
    name = "ui",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/domain/registry/config",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
    ],
)


closure_js_deps(
    name = "deps",
    srcs = [
        "//java/com/google/domain/registry/ui/js",
        "//java/com/google/domain/registry/ui/js/admin",
        "//java/com/google/domain/registry/ui/js/registrar",
    ],
)

SOURCEMAP_SCRUB = ("  -e 's@b....-out/[^/]*/bin/@@g'" +
                   "  -e 's@b....-out/[^/]*/genfiles/@@g'" +
                   "  -e 's@\"java/@\"/assets/sources/java/@g'" +
                   "  -e 's@\"javascript/@\"/assets/sources/javascript/@g'" +
                   "  -e 's@\"third_party/@\"/assets/sources/third_party/@g'" +
                   "  -e 's@\"external/@\"/assets/sources/external/@g'" +
                   "  -e 's@sources/java/.*/admin_bin.js@js/admin_bin_map.js@g'")

################################################################################
## Registrar Console

# This is the full-blown compiled JavaScript source code for the registrar
# console. Everything, including the soy templates, is compiled into a single
# .js file. The only symbols that will be available are the ones you @export.
# This will also replace calls to goog.getCssName() and {css ...} with their
# non-union minified equivalents.
closure_js_binary(
    name = "registrar_bin",
    externs_list = ["//java/com/google/domain/registry/ui/externs"],
    main = "registry.registrar.main",
    deps = [
        "//java/com/google/domain/registry/ui/css:registrar_bin",
        "//java/com/google/domain/registry/ui/js/registrar",
    ],
)

# Mangle all the paths in the generated sourcemap so they're absolute; assuming
# the codebase is available under /assets/sources/.
genrule(
    name = "registrar_bin_sourcemap",
    srcs = ["registrar_bin.sourcemap"],
    outs = ["registrar_bin.js.map"],
    cmd = "sed $(location registrar_bin.sourcemap) " + SOURCEMAP_SCRUB + "  >$@",
)

# The webserver should provide this to trusted admin users, rather than
# registrar_bin.js. This is what makes debugging in production possible.
genrule(
    name = "registrar_mapped",
    srcs = ["registrar_bin.js"],
    outs = ["registrar_bin_map.js"],
    cmd = "cat $(location registrar_bin.js) >$@ && " +
          "echo '//# sourceMappingURL=registrar_bin.js.map' >>$@",
)

# This target creates a compiled JavaScript file where symbols are renamed to
# include dollar signs. This is useful for testing, because you can still read
# the source code, but it'll fail if your code is incorrect with regard to
# dotted and quoted properties. The same applies to CSS class names, which get
# an extra underscore.
closure_js_binary(
    name = "registrar_dbg",
    debug = 1,
    externs_list = ["//java/com/google/domain/registry/ui/externs"],
    formatting = "PRETTY_PRINT",
    main = "registry.registrar.main",
    deps = [
        "//java/com/google/domain/registry/ui/css:registrar_dbg",
        "//java/com/google/domain/registry/ui/js/registrar",
    ],
)

################################################################################
## Braintree Payment Method Frame (Brainframe)

closure_js_binary(
    name = "brain_bin",
    externs_list = ["//java/com/google/domain/registry/ui/externs"],
    main = "registry.registrar.BrainFrame.main",
    deps = ["//java/com/google/domain/registry/ui/js/registrar"],
)

genrule(
    name = "brain_bin_sourcemap",
    srcs = ["brain_bin.sourcemap"],
    outs = ["brain_bin.js.map"],
    cmd = "sed $(location brain_bin.sourcemap) " + SOURCEMAP_SCRUB + "  >$@",
)

genrule(
    name = "brain_mapped",
    srcs = ["brain_bin.js"],
    outs = ["brain_bin_map.js"],
    cmd = "cat $(location brain_bin.js) >$@ && " +
          "echo '//# sourceMappingURL=brain_bin.js.map' >>$@",
)

################################################################################
## Admin Console

closure_js_binary(
    name = "admin_bin",
    externs_list = ["//java/com/google/domain/registry/ui/externs"],
    main = "registry.admin.main",
    deps = [
        "//java/com/google/domain/registry/ui/css:admin_bin",
        "//java/com/google/domain/registry/ui/js/admin",
    ],
)

genrule(
    name = "admin_bin_sourcemap",
    srcs = ["admin_bin.sourcemap"],
    outs = ["admin_bin.js.map"],
    cmd = "sed $(location admin_bin.sourcemap) " + SOURCEMAP_SCRUB + "  >$@",
)

genrule(
    name = "admin_mapped",
    srcs = ["admin_bin.js"],
    outs = ["admin_bin_map.js"],
    cmd = "cat $(location admin_bin.js) >$@ && " +
          "echo '//# sourceMappingURL=admin_bin.js.map' >>$@",
)

closure_js_binary(
    name = "admin_dbg",
    debug = 1,
    externs_list = ["//java/com/google/domain/registry/ui/externs"],
    formatting = "PRETTY_PRINT",
    main = "registry.admin.main",
    deps = [
        "//java/com/google/domain/registry/ui/css:admin_dbg",
        "//java/com/google/domain/registry/ui/js/admin",
    ],
)
