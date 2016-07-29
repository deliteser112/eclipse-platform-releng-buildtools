workspace(name = "domain_registry")

load("//java/google/registry:repositories.bzl", "domain_registry_repositories")

domain_registry_repositories()

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "8c8a0f7f1327178bc8654e658cb6fff1171936e3033c5e263d513a7901a75b31",
    strip_prefix = "rules_closure-0.2.5",
    url = "http://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_closure/archive/0.2.5.tar.gz",
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories(
    omit_gson = True,
    omit_guava = True,
    omit_icu4j = True,
    omit_json = True,
    omit_jsr305 = True,
    omit_jsr330_inject = True,
    omit_protobuf_java = True,
)
