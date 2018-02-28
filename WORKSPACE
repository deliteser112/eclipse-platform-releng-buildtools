workspace(name = "domain_registry")

# https://github.com/bazelbuild/rules_closure/releases/tag/0.4.2
http_archive(
    name = "io_bazel_rules_closure",
    strip_prefix = "rules_closure-08039ba8ca59f64248bb3b6ae016460fe9c9914f",
    sha256 = "6691c58a2cd30a86776dd9bb34898b041e37136f2dc7e24cadaeaf599c95c657",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_closure/archive/08039ba8ca59f64248bb3b6ae016460fe9c9914f.tar.gz",
        "https://github.com/bazelbuild/rules_closure/archive/08039ba8ca59f64248bb3b6ae016460fe9c9914f.tar.gz",  # 2018-01-16
    ],
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories(
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
