package(
    default_testonly = 1,
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

java_library(
    name = "model",
    # There be circular dependencies down thar, so just have this
    # BUILD file glob them in.
    srcs = glob([
        "*.java",
        "**/*.java",
    ]),
    resources = [
    ] + glob(["**/testdata/*"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/dns/writer",
        "//java/google/registry/flows",
        "//java/google/registry/model",
        "//java/google/registry/util",
        "//java/google/registry/xml",
        "//javatests/google/registry/testing",
        "//javatests/google/registry/xml",
        "//third_party/objectify:objectify-v4_1",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_testing",
        "@com_google_code_findbugs_jsr305",
        "@com_google_flogger",
        "@com_google_flogger_system_backend",
        "@com_google_guava",
        "@com_google_guava_testlib",
        "@com_google_monitoring_client_contrib",
        "@com_google_truth",
        "@com_google_truth_extensions_truth_java8_extension",
        "@com_sun_activation_javax_activation",
        "@javax_xml_ws_jaxws_api",
        "@joda_time",
        "@junit",
        "@org_joda_money",
        "@org_mockito_all",
    ],
)

GenTestRules(
    name = "GeneratedTestRules",
    medium_tests = ["registry/label/PremiumListTest"],
    test_files = glob([
        "*Test.java",
        "**/*Test.java",
    ]),
    deps = [":model"],
)
