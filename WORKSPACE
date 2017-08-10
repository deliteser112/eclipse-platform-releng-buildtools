workspace(name = "domain_registry")

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "286e9169d478c4549199e862d63b24aa8530e0fe1c1fd44524d307f3a56b0fa0",
    strip_prefix = "rules_closure-c8e68361db0875f6fabb23abed16f1cebbb8b5d5",
    urls = [
        "http://mirror.bazel.build/github.com/bazelbuild/rules_closure/archive/c8e68361db0875f6fabb23abed16f1cebbb8b5d5.tar.gz", # 2017-08-08
        "https://github.com/bazelbuild/rules_closure/archive/c8e68361db0875f6fabb23abed16f1cebbb8b5d5.tar.gz",
    ],
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories(
    omit_com_google_code_findbugs_jsr305 = True,
    omit_com_google_guava = True,
    omit_com_ibm_icu_icu4j = True,
    omit_javax_inject = True,
    omit_org_json = True,
    omit_com_google_template_soy = True,
)

load("//java/google/registry:repositories.bzl", "domain_registry_repositories")

domain_registry_repositories()
