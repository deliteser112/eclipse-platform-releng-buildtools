package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "backend",
    srcs = glob(["*.java"]),
    resources = glob(["testdata/*"]),
    runtime_deps = [
        # TODO(b/19332643): Remove this dependency when Modules is lazy.
        "//third_party/java/appengine:appengine-api-testonly",
    ],
    deps = [
        "//java/com/google/domain/registry/module/backend",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/servlet/servlet_api",
        "//third_party/java/truth",
    ],
)

load("//java/com/google/testing/builddefs:GenTestRules.bzl", "GenTestRules")

GenTestRules(
    name = "GeneratedTestRules",
    test_files = glob(["*Test.java"]),
    deps = [":backend"],
)
