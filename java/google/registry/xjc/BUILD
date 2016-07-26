package(
    default_visibility = ["//java/google/registry:registry_project"],
)

licenses(["notice"])  # Apache 2.0

load("//java/google/registry/xjc:generated_files.bzl", "pkginfo_generated_files", "xjc_generated_files")


sh_binary(
    name = "list_generated_files",
    srcs = ["list_generated_files.sh"],
    data = [
        "bindings.xjb",
        "//third_party/java/jaxb:jaxb-xjc",
        "//java/google/registry/xml:xml_schema_files",
    ],
)

genrule(
    name = "jaxb_srcs",
    srcs = [
        "bindings.xjb",
        "//java/google/registry/xml:xml_schema_files",
    ],
    outs = xjc_generated_files,
    cmd = " && ".join([
        "XJC=$$PWD/$(location //third_party/java/jaxb:jaxb-xjc)",
        "OUT=$$PWD/$(GENDIR)/java",
        "mkdir -p $$OUT",
        "cp $(SRCS) /tmp",
        "cd /tmp",
        "$$XJC -npa -quiet -extension -d $$OUT -b *.xjb *.xsd",
        "find $$OUT -name '*.java' | xargs sed -i 's/" +
        "public class/" +
        "@SuppressWarnings({\"unchecked\",\"serial\",\"cast\"}) " +
        "public class/'",
    ]),
    compatible_with = ["//buildenv/target:appengine"],
    tools = ["//third_party/java/jaxb:jaxb-xjc"],
)

sh_binary(
    name = "make_pkginfo",
    srcs = ["make_pkginfo.sh"],
)

genrule(
    name = "pkginfo_srcs",
    srcs = ["package-info.java.in"],
    outs = pkginfo_generated_files,
    cmd = "$(location :make_pkginfo) $(SRCS) $(@D)",
    compatible_with = ["//buildenv/target:appengine"],
    tools = [
        ":jaxb_srcs",
        ":make_pkginfo",
    ],
)

java_library(
    name = "xjc",
    srcs = glob(["*.java"]) + xjc_generated_files + pkginfo_generated_files,
    compatible_with = ["//buildenv/target:appengine"],
    deps = [
        "//java/com/google/common/collect",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
        "//java/google/registry/xml",
    ],
)
