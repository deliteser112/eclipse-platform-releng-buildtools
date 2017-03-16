package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

java_binary(
    name = "annotation_processors_ide",
    create_executable = False,
    runtime_deps = [
        "@com_google_auto_common",
        "@com_google_auto_factory//:processor",
        "@com_google_auto_service//:processor",
        "@com_google_auto_value//:processor",
        "@com_google_dagger_compiler",
        "@com_squareup_javawriter",
    ],
)

java_binary(
    name = "registry_deps",
    testonly = 1,
    create_executable = 0,
    runtime_deps = [
        "//java/google/registry/module/backend",
        "//java/google/registry/module/frontend",
        "//java/google/registry/module/tools",
        "//java/google/registry/tools",
        "@com_google_apis_google_api_services_cloudkms",
        "@com_google_appengine_api_1_0_sdk//:link",
        "@com_google_appengine_api_stubs",
        "@com_google_appengine_remote_api//:link",
        "@com_google_appengine_testing",
        "@com_google_appengine_tools_sdk",
        "@com_google_guava_testlib",
        "@com_google_truth",
        "@junit",
        "@org_apache_ftpserver_core",
        "@org_apache_sshd_core",
        "@org_hamcrest_library",
        "@org_mockito_all",
        "@org_mortbay_jetty",
    ],
)

# This rule creates a copy of the registry_deps_deploy.jar with all of the
# domain-registry code removed. This is to avoid having duplicate instances
# of domain-registry classes on the eclipse build path.
genrule(
    name = "eclipse_deps",
    srcs = ["//java/google/registry/eclipse:registry_deps_deploy.jar"],
    outs = ["eclipse_deps.jar"],
    cmd = " && ".join([
        "JAR=$$(pwd)/$(location @local_jdk//:bin/jar)",
        "IN=$$(pwd)/$(SRCS)",
        "OUT=$$(pwd)/$@",
        "TMP=$$(mktemp -d $${TMPDIR:-/tmp}/eclipse_deps.XXXXXXXX)",
        "cd $$TMP",
        "$$JAR -xf $$IN",
        "rm -rf google/registry",
        "$$JAR -cmf META-INF/MANIFEST.MF eclipse_deps.jar .",
        "mv eclipse_deps.jar $$OUT",
        "rm -rf $$TMP",
    ]),
    tools = [
        "@local_jdk//:bin/jar",
        "@local_jdk//:jdk-lib",
        "@local_jdk//:jre-default",
    ],
)
