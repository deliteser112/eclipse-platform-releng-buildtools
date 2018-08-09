package(default_visibility = ["//visibility:public"])

licenses(["reciprocal"])  # CDDL 1.1 (also dual-licensed under GPL v2)

java_library(
    name = "jaxb",
    exports = ["//third_party/jaxb/v2_3_0"],
)

java_binary(
    name = "jaxb-xjc",
    main_class = "com.sun.tools.xjc.XJCFacade",
    runtime_deps = [
        ":jaxb",
    ],
)
