package_group(
    name = "specific_version",
    packages = ["//third_party/jaxb"],
)

package(default_visibility = ["//visibility:public"])

licenses(["reciprocal"])  # CDDL 1.1 (also dual-licensed under GPL v2)

java_library(
    name = "v2_3_0",
    exports = [
        "@com_sun_xml_bind_jaxb_core",
        "@com_sun_xml_bind_jaxb_impl",
        "@com_sun_xml_bind_jaxb_jxc",
        "@com_sun_xml_bind_jaxb_xjc",
        "@javax_xml_bind_jaxb_api",
    ],
    runtime_deps = [
        "//third_party/activation",
    ],
)
