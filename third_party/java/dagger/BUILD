licenses(["notice"])  # Apache License 2.0

java_library(
    name = "dagger",
    exported_plugins = [":component-codegen"],
    visibility = ["//visibility:public"],
    exports = [":dagger-runtime"],
)

java_library(
    name = "dagger-runtime",
    exports = [
        "@bazel_tools//third_party:jsr330_inject",
        "@dagger//jar",
    ],
)

java_library(
    name = "dagger-producers",
    exports = [
        "@bazel_tools//third_party:guava",
        "@bazel_tools//third_party:jsr330_inject",
        "@dagger_producers//jar",
    ],
)

java_library(
    name = "dagger-compiler",
    exports = [
        "@bazel_tools//third_party:auto_common",
        "@bazel_tools//third_party:auto_service",
        "@bazel_tools//third_party:auto_value",
        "@bazel_tools//third_party:gson",
        "@bazel_tools//third_party:guava",
        "@bazel_tools//third_party:jsr305",
        "@bazel_tools//third_party:jsr330_inject",
        "@dagger_compiler//jar",
        ":dagger-producers",
        ":dagger-runtime",
        "//third_party/java/javapoet",
    ],
)

java_plugin(
    name = "component-codegen",
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    deps = [":dagger-compiler"],
)
