workspace(name = "domain_registry")

# https://github.com/bazelbuild/rules_closure/releases/tag/0.8.0
http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "b29a8bc2cb10513c864cb1084d6f38613ef14a143797cea0af0f91cd385f5e8c",
    strip_prefix = "rules_closure-0.8.0",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_closure/archive/0.8.0.tar.gz",
        "https://github.com/bazelbuild/rules_closure/archive/0.8.0.tar.gz",
    ],
)
load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories(
    omit_com_google_auto_factory = True,
    omit_com_google_code_findbugs_jsr305 = True,
    omit_com_google_guava = True,
    omit_com_ibm_icu_icu4j = True,
    omit_javax_inject = True,
    omit_org_json = True,
)

load("//java/google/registry:repositories.bzl", "domain_registry_repositories")

domain_registry_repositories()

# Setup docker bazel rules
git_repository(
    name = "io_bazel_rules_docker",
    remote = "https://github.com/bazelbuild/rules_docker.git",
    tag = "v0.4.0",
)

load(
    "@io_bazel_rules_docker//container:container.bzl",
    "container_pull",
    container_repositories = "repositories",
)

container_repositories()

container_pull(
  name = "java_base",
  registry = "gcr.io",
  repository = "distroless/java",
  digest = "sha256:780ee786a774a25a4485f491b3e0a21f7faed01864640af7cebec63c46a0845a",
)
