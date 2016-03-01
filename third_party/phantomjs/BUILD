package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # BSD

genrule(
    name = "phantomjs_bin",
    srcs = ["@phantomjs//file"],
    outs = ["phantomjs"],
    cmd = " && ".join([
        "IN=$$(pwd)/$(SRCS)",
        "OUT=$$(pwd)/$@",
        "TMP=$$(mktemp -d $${TMPDIR:-/tmp}/phantomjs.XXXXXXXX)",
        "cd $$TMP",
        "tar -xjf $$IN",
        "cd phantomjs-*",
        "mv bin/phantomjs $$OUT",
        "rm -rf $$TMP",
    ]),
)
