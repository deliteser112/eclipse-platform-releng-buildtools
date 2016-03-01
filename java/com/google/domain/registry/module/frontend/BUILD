package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "frontend",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/braintree",
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/keyring/api",
        "//java/com/google/domain/registry/rdap",
        "//java/com/google/domain/registry/request",
        "//java/com/google/domain/registry/request:modules",
        "//java/com/google/domain/registry/ui",
        "//java/com/google/domain/registry/ui/server/registrar",
        "//java/com/google/domain/registry/util",
        "//java/com/google/domain/registry/whois",
        "//third_party/java/bouncycastle",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)

# This rule is used so bazel can generate "frontend_jar_deploy.jar" (which
# contains transitive dependencies) for deployment to App Engine. It MUST
# explicitly depend upon upon anything loaded at runtime, e.g. old servlets
# referenced by the module's web.xml file, that isn't statically linked above.
java_binary(
    name = "frontend_jar",
    create_executable = 0,
    runtime_deps = [
        ":frontend",
        "//java/com/google/domain/registry/monitoring/whitebox",  # MetricsTaskServlet
        "//java/com/google/domain/registry/ui/server/admin",  # AdminUiServlet, etc.
        "//java/com/google/domain/registry/ui/server/api",  # CheckApiServlet
        "//java/com/google/domain/registry/ui/server/registrar",  # ConsoleUiServlet, etc.
    ],
)
