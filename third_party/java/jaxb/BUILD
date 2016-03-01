package(default_visibility = ["//visibility:public"])

licenses(["reciprocal"])  # CDDL 1.1 (also dual-licensed under GPL v2)

java_library(
    name = "jaxb",
    exports = ["@jaxb_api//jar"],
)

java_binary(
    name = "jaxb-xjc",
    main_class = "com.sun.tools.xjc.XJCFacade",
    runtime_deps = [
        "@jaxb_api//jar",
        "@jaxb_core//jar",
        "@jaxb_impl//jar",
        "@jaxb_xjc//jar",
    ],
)
