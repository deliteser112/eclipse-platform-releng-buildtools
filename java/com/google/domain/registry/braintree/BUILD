package(default_visibility = ["//java/com/google/domain/registry:registry_project"])


java_library(
    name = "braintree",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/domain/registry/config",
        "//java/com/google/domain/registry/keyring/api",
        "//third_party/java/braintree",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
    ],
)
