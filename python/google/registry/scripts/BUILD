package(default_visibility = ["//java/google/registry:registry_project"])

licenses(["notice"])  # Apache 2.0

py_binary(
    name = "xml_to_index_yaml_translator",
    srcs = ["xml_to_index_yaml_translator.py"],
    python_version = "PY2",
    deps = [":xml_to_index_yaml_translator_lib"],
)

py_library(
    name = "xml_to_index_yaml_translator_lib",
    srcs = ["xml_to_index_yaml_translator.py"],
    deps = ["//python:python_directory_import"],
)

py_test(
    name = "xml_to_index_yaml_translator_test",
    size = "small",
    srcs = ["xml_to_index_yaml_translator_test.py"],
    data = [
        "testdata/datastore-indexes.xml",
        "testdata/index.yaml",
    ],
    python_version = "PY2",
    deps = [":xml_to_index_yaml_translator_lib"],
)
