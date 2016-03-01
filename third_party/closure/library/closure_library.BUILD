package(default_visibility = ["//visibility:public"])

filegroup(
    name = "js_files",
    srcs = glob(
        [
            "closure/goog/**/*.js",
            "third_party/closure/goog/**/*.js",
        ],
        exclude = [
            "closure/goog/**/*_test.js",
            "closure/goog/demos/**/*.js",
            "third_party/closure/goog/**/*_test.js",
        ],
    ),
)

filegroup(
    name = "css_files",
    srcs = glob(["closure/goog/css/**/*.css"]),
)

py_binary(
    name = "depswriter",
    srcs = [
        "closure/bin/build/depswriter.py",
        "closure/bin/build/source.py",
        "closure/bin/build/treescan.py",
    ],
    main = "closure/bin/build/depswriter.py",
)
