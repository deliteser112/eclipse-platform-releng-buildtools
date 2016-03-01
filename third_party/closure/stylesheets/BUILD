package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

java_binary(
    name = "stylesheets",
    jvm_flags = ["-client"],
    main_class = "com.google.common.css.compiler.commandline.ClosureCommandLineCompiler",
    runtime_deps = [
        "@args4j//jar",
        "@bazel_tools//third_party:gson",
        "@bazel_tools//third_party:guava",
        "@bazel_tools//third_party:jsr305",
        "@closure_stylesheets//jar",
    ],
)
