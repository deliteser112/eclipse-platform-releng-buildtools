package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

load("//third_party/closure/compiler:closure_js_library.bzl", "closure_js_library")

java_library(
    name = "templates",
    exports = ["@soy//jar"],
    runtime_deps = [
        "@aopalliance//jar",
        "@asm//jar",
        "@asm_analysis//jar",
        "@asm_commons//jar",
        "@asm_util//jar",
        "@bazel_tools//third_party:guava",
        "@bazel_tools//third_party:jsr305",
        "@bazel_tools//third_party:jsr330_inject",
        "@guice//jar",
        "@guice_assistedinject//jar",
        "@guice_multibindings//jar",
        "@icu4j//jar",
    ],
)

closure_js_library(
    name = "soyutils_usegoog",
    srcs = ["@soyutils_usegoog//file"],
    deps = ["//third_party/closure/library"],
)

java_binary(
    name = "SoyParseInfoGenerator",
    jvm_flags = ["-client"],
    main_class = "com.google.template.soy.SoyParseInfoGenerator",
    runtime_deps = [
        "@args4j//jar",
        ":templates",
    ],
)

java_binary(
    name = "SoyToJsSrcCompiler",
    jvm_flags = ["-client"],
    main_class = "com.google.template.soy.SoyToJsSrcCompiler",
    runtime_deps = [
        "@args4j//jar",
        ":templates",
    ],
)
