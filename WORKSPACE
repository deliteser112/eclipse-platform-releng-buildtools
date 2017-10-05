workspace(name = "domain_registry")

# https://github.com/bazelbuild/rules_closure/releases/tag/0.4.2
http_archive(
    name = "io_bazel_rules_closure",
    strip_prefix = "rules_closure-0.4.2",
    sha256 = "25f5399f18d8bf9ce435f85c6bbf671ec4820bc4396b3022cc5dc4bc66303609",
    urls = [
        "http://mirror.bazel.build/github.com/bazelbuild/rules_closure/archive/0.4.2.tar.gz",
        "https://github.com/bazelbuild/rules_closure/archive/0.4.2.tar.gz",
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
