package_group(
    name = "specific_version",
    packages = ["//third_party/java/jsch"],
)

package(default_visibility = [":specific_version"])

licenses(["notice"])  # BSD

java_import(
    name = "v0_1_44_google",
    jars = ["jsch-0.1.44_google.jar"],
    srcjar = "jsch-0.1.44_google-src.jar",
    deps = ["@com_jcraft_jzlib"],
)
