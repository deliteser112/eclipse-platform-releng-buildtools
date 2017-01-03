workspace(name = "domain_registry")

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "c104d30b4aaf23d72afe327b4478d1c08cf1ff75c6db2060682bb7ad0591e19b",
    strip_prefix = "rules_closure-962d929bc769fc320dd395f54fef3e9db62c3920",
    urls = [
        "http://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_closure/archive/962d929bc769fc320dd395f54fef3e9db62c3920.tar.gz",  # 2016-12-28
        "https://github.com/bazelbuild/rules_closure/archive/962d929bc769fc320dd395f54fef3e9db62c3920.tar.gz",
    ],
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories(
    omit_com_google_code_findbugs_jsr305 = True,
    omit_com_google_guava = True,
    omit_com_ibm_icu_icu4j = True,
    omit_javax_inject = True,
    omit_org_apache_tomcat_servlet_api = True,
    omit_org_json = True,
    omit_org_mortbay_jetty = True,
    omit_org_mortbay_jetty_util = True,
)

load("//java/google/registry:repositories.bzl", "domain_registry_repositories")

domain_registry_repositories()
