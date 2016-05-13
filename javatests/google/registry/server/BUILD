package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "TestServer",
    visibility = ["//visibility:public"],
    srcs = [
        "HealthzServlet.java",
        "Route.java",
        "ServletWrapperDelegatorServlet.java",
        "StaticResourceServlet.java",
        "TestServer.java",
        "UrlChecker.java",
    ],
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/common/primitives",
        "//java/com/google/common/util/concurrent",
        "//java/com/google/domain/registry/util",
        "//third_party/java/auto:auto_value",
        "//third_party/java/jetty/v6_1_22",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/servlet/servlet_api",
    ],
)

java_library(
    name = "Fixture",
    srcs = ["Fixture.java"],
    deps = [
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/model",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/joda_time",
    ],
)

java_library(
    name = "RegistryTestServer",
    srcs = ["RegistryTestServer.java"],
    data = ["//java/com/google/domain/registry/ui:runfiles_debug"],
    deps = [
        ":TestServer",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/flows",
        "//java/com/google/domain/registry/module/backend",
        "//java/com/google/domain/registry/module/frontend",
        "//java/com/google/domain/registry/ui/server/api",
        "//java/com/google/domain/registry/ui/server/registrar",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/servlet/servlet_api",
    ],
)

java_binary(
    name = "server",
    srcs = ["RegistryTestServerMain.java"],
    main_class = "com.google.domain.registry.server.RegistryTestServerMain",
    deps = [
        ":Fixture",
        ":RegistryTestServer",
        "//java/com/google/common/collect",
        "//java/com/google/common/net",
        "//java/com/google/domain/registry/tools/params",
        "//java/com/google/domain/registry/ui",
        "//javatests/com/google/domain/registry/testing",
        "//third_party/java/jcommander",
        "//third_party/java/junit",
    ],
)
