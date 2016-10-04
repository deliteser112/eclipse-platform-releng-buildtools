workspace(name = "domain_registry")

load("//java/google/registry:repositories.bzl", "domain_registry_repositories")

domain_registry_repositories()

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "7d75688c63ac09a55ca092a76c12f8d1e9ee8e7a890f3be6594a4e7d714f0e8a",
    strip_prefix = "rules_closure-b8841276e73ca677c139802f1168aaad9791dec0",
    url = "http://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_closure/archive/b8841276e73ca677c139802f1168aaad9791dec0.tar.gz",  # 2016-10-02
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories(
    omit_gson = True,
    omit_guava = True,
    omit_icu4j = True,
    omit_json = True,
    omit_jsr305 = True,
    omit_jsr330_inject = True,
)
