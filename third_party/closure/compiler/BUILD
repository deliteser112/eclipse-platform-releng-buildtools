package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache License 2.0

java_binary(
    name = "compiler",
    jvm_flags = ["-client"],
    main_class = "com.google.javascript.jscomp.CommandLineRunner",
    runtime_deps = ["@closure_compiler//jar"],
)
