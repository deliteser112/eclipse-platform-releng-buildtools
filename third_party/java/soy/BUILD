package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

java_library(
    name = "soy",
    exports = ["@soy//jar"],
    runtime_deps = [
        "@aopalliance//jar",
        "@asm//jar",
        "@asm_analysis//jar",
        "@asm_commons//jar",
        "@asm_util//jar",
        "@guava//jar",
        "@guice//jar",
        "@guice_assistedinject//jar",
        "@guice_multibindings//jar",
        "@icu4j//jar",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
    ],
)

java_binary(
    name = "SoyParseInfoGenerator",
    jvm_flags = ["-client"],
    main_class = "com.google.template.soy.SoyParseInfoGenerator",
    runtime_deps = [
        "@args4j//jar",
        ":soy",
    ],
)
