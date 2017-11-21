package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "proxy",
    srcs = glob(["**/*.java"]),
    resources = glob(["testdata/*.xml"]),
    deps = [
        "//java/google/registry/monitoring/metrics",
        "//java/google/registry/monitoring/metrics/contrib",
        "//java/google/registry/proxy",
        "//java/google/registry/util",
        "//javatests/google/registry/testing",
        "@com_beust_jcommander",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_truth",
        "@com_google_truth_extensions_truth_java8_extension",
        "@io_netty_buffer",
        "@io_netty_codec",
        "@io_netty_codec_http",
        "@io_netty_common",
        "@io_netty_handler",
        "@io_netty_transport",
        "@joda_time",
        "@junit",
        "@org_bouncycastle_bcpkix_jdk15on",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(
        ["**/*Test.java"],
        exclude = ["ProtocolModuleTest.java"],
    ),
    deps = [":proxy"],
)
