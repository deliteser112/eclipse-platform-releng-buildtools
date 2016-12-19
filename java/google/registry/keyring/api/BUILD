package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "api",
    srcs = glob(["*.java"]),
    resources = glob(["*.asc"]),
    deps = [
        "@com_google_code_findbugs_jsr305",
        "@com_google_dagger",
        "@com_google_guava",
        "@org_bouncycastle_bcpg_jdk15on",
        "@org_bouncycastle_bcpkix_jdk15on",
    ],
)
