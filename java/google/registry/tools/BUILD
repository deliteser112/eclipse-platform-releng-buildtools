package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

# Restrict visibility to :tools because :remoteapi-internal (and transitively
# :appengine-api-link) should never be linked into an App Engine deploy jar,
# since the App Engine API is provided by the runtime environment.
package_group(
    name = "allowed-tools",
    packages = [
        "//java/google/registry/eclipse",
        "//java/google/registry/testing",
        "//java/google/registry/tools",
        "//javatests/google/registry/tools",
    ],
)

java_library(
    name = "tools",
    srcs = glob([
        "*.java",
        "javascrap/*.java",
    ]),
    resources = glob([
        "*.properties",
        "sql/*.sql",
    ]),
    visibility = [":allowed-tools"],
    deps = [
        "//java/google/registry/bigquery",
        "//java/google/registry/config",
        "//java/google/registry/dns",
        "//java/google/registry/dns/writer",
        "//java/google/registry/dns/writer/clouddns",
        "//java/google/registry/dns/writer/dnsupdate",
        "//java/google/registry/export",
        "//java/google/registry/flows",
        "//java/google/registry/gcs",
        "//java/google/registry/keyring/api",
        "//java/google/registry/model",
        "//java/google/registry/pricing",
        "//java/google/registry/rde",
        "//java/google/registry/request:modules",
        "//java/google/registry/security",
        "//java/google/registry/tldconfig/idn",
        "//java/google/registry/tmch",
        "//java/google/registry/tools/params",
        "//java/google/registry/tools/server",
        "//java/google/registry/tools/soy:soy_java_wrappers",
        "//java/google/registry/util",
        "//java/google/registry/whois",
        "//java/google/registry/xjc",
        "//java/google/registry/xml",
        "//third_party/java/objectify:objectify-v4_1",
        "@com_beust_jcommander",
        "@com_google_api_client",
        "@com_google_apis_google_api_services_bigquery",
        "@com_google_appengine_api_1_0_sdk",
        "@com_google_appengine_remote_api",
        "@com_google_appengine_remote_api//:link",
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_guava",
        "@com_google_http_client",
        "@com_google_http_client_jackson2",
        "@com_google_re2j",
        "@com_googlecode_json_simple",
        "@io_bazel_rules_closure//closure/templates",
        "@joda_time",
        "@org_bouncycastle_bcpg_jdk15on",
        "@org_bouncycastle_bcpkix_jdk15on",
        "@org_joda_money",
        "@org_json",
    ],
)

java_binary(
    name = "nomulus",
    create_executable = 1,
    main_class = "google.registry.tools.RegistryTool",
    runtime_deps = [
        ":tools",
        "@com_google_appengine_api_1_0_sdk//:link",
        "@com_google_appengine_remote_api//:link",
    ],
)
