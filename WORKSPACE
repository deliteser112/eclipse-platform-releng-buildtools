workspace(name = "domain_registry")

load("//java/google/registry:repositories.bzl", "domain_registry_repositories")

domain_registry_repositories()

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "1e2e6f73c4bb219a37a667ecb637539d7d7839f99b4f97496e5ea5e16cc87431",
    strip_prefix = "rules_closure-b2ff976c8585e2051153bd62fbef6ef176b41b42",
    url = "http://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_closure/archive/b2ff976c8585e2051153bd62fbef6ef176b41b42.tar.gz",  # 2016-11-29
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories(
    omit_gson = True,
    omit_guava = True,
    omit_icu4j = True,
    omit_jetty = True,
    omit_jetty_util = True,
    omit_json = True,
    omit_jsr305 = True,
    omit_jsr330_inject = True,
    omit_servlet_api = True,
)
