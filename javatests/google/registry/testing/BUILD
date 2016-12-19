package(
    default_testonly = 1,
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "testing",
    srcs = glob(
        ["*.java"],
        exclude = [
            "FakeProxyConnectionMetadataProvider.java",
            "FakeQuotaServerClient.java",
        ],
    ),
    resources = [
        "logging.properties",
        "//java/google/registry:env/common/default/WEB-INF/datastore-indexes.xml",
        "//java/google/registry:env/common/default/WEB-INF/queue.xml",
    ] + glob(["*.csv"]),
    deps = [
        "//java/google/registry/config",
        "//java/google/registry/dns:constants",
        "//java/google/registry/dns/writer",
        "//java/google/registry/flows",
        "//java/google/registry/model",
        "//java/google/registry/pricing",
        "//java/google/registry/request",
        "//java/google/registry/tmch",
        "//java/google/registry/util",
        "//java/google/registry/xml",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_google_appengine_api_1_0_sdk//:testonly",
        "@com_google_appengine_api_stubs",
        "@com_google_appengine_testing",
        "@com_google_appengine_tools_appengine_gcs_client",
        "@com_google_appengine_tools_sdk",
        "@com_google_auto_value",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava",
        "@com_google_guava_testlib",
        "@com_google_truth",
        "@com_googlecode_json_simple",
        "@javax_inject",
        "@javax_servlet_api",
        "@joda_time",
        "@junit",
        "@org_bouncycastle_bcpg_jdk15on",
        "@org_bouncycastle_bcpkix_jdk15on",
        "@org_joda_money",
        "@org_json",
        "@org_mockito_all",
    ],
)
