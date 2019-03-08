workspace(name = "domain_registry")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# https://github.com/bazelbuild/rules_closure/releases/tag/0.8.0
http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "0e6de40666f2ebb2b30dc0339745a274d9999334a249b05a3b1f46462e489adf",
    strip_prefix = "rules_closure-87d24b1df8b62405de8dd059cb604fd9d4b1e395",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_closure/archive/87d24b1df8b62405de8dd059cb604fd9d4b1e395.tar.gz",
        "https://github.com/bazelbuild/rules_closure/archive/87d24b1df8b62405de8dd059cb604fd9d4b1e395.tar.gz",
    ],
)
load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories(
    omit_com_google_auto_factory = True,
    omit_com_google_protobuf = True,
    omit_com_google_code_findbugs_jsr305 = True,
    omit_com_google_guava = True,
    omit_com_ibm_icu_icu4j = True,
    omit_javax_inject = True,
    omit_org_json = True,
)

load("//java/google/registry:repositories.bzl", "domain_registry_repositories")

domain_registry_repositories()

# Setup docker bazel rules
http_archive(
    name = "io_bazel_rules_docker",
    sha256 = "29d109605e0d6f9c892584f07275b8c9260803bf0c6fcb7de2623b2bedc910bd",
    strip_prefix = "rules_docker-0.5.1",
    urls = ["https://github.com/bazelbuild/rules_docker/archive/v0.5.1.tar.gz"],
)

load(
    "@io_bazel_rules_docker//container:container.bzl",
    "container_pull",
    container_repositories = "repositories",
)

# This is NOT needed when going through the language lang_image
# "repositories" function(s).
container_repositories()

container_pull(
  name = "java_base",
  registry = "gcr.io",
  repository = "distroless/java",
  # 'tag' is also supported, but digest is encouraged for reproducibility.
  digest = "sha256:8c1769cb253bdecc257470f7fba05446a55b70805fa686f227a11655a90dfe9e",
)
