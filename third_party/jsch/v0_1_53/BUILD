package_group(
    name = "specific_version",
    packages = ["//third_party/jsch"],
)

package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # BSD

java_import(
    name = "v0_1_53",
    jars = ["jsch-0.1.53.jar"],
    srcjar = "jsch-0.1.53-src.jar",
    deps = ["@com_jcraft_jzlib"],
)
