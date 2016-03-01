package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)


java_library(
    name = "sftp",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/util",
        "//third_party/java/apache_sshd",
        "//third_party/java/bouncycastle",
        "//third_party/java/bouncycastle_bcpg",
        "//third_party/java/ftpserver",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/junit",
    ],
)
