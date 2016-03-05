package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

java_binary(
    name = "stylesheets",
    jvm_flags = ["-client"],
    main_class = "com.google.common.css.compiler.commandline.ClosureCommandLineCompiler",
    runtime_deps = [
        "@args4j//jar",
        "@closure_stylesheets//jar",
        "@gson//jar",
        "@guava//jar",
        "//third_party/java/jsr305_annotations",
    ],
)
