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
        "@dagger//jar",
        "@javax_inject",
    ],
)

java_library(
    name = "dagger-producers",
    exports = [
        "@dagger_producers//jar",
        "@guava//jar",
        "@javax_inject",
    ],
)

java_library(
    name = "dagger-compiler",
    visibility = ["//visibility:public"],
    exports = ["@dagger_compiler//jar"],
    runtime_deps = [
        ":dagger-producers",
        ":dagger-runtime",
        "//third_party/java/auto:auto_common",
        "//third_party/java/auto:auto_service",
        "//third_party/java/javapoet",
        "@com_google_auto_value",
        "@com_google_code_findbugs_jsr305",
        "@gson//jar",
        "@guava//jar",
        "@javax_inject",
    ],
)

java_plugin(
    name = "component-codegen",
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    deps = [":dagger-compiler"],
)
