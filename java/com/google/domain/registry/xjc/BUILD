package(
    default_visibility = ["//java/com/google/domain/registry:registry_project"],
)

load("//java/com/google/domain/registry/xjc:generated_files.bzl", "pkginfo_generated_files", "xjc_generated_files")


sh_binary(
    name = "list_generated_files",
    srcs = ["list_generated_files.sh"],
    data = [
        "bindings.xjb",
        "//java/com/google/domain/registry/xml:xml_schema_files",
        "//third_party/java/jaxb:jaxb-xjc",
    ],
)

genrule(
    name = "jaxb_srcs",
    srcs = [
        "bindings.xjb",
        "//java/com/google/domain/registry/xml:xml_schema_files",
    ],
    outs = xjc_generated_files,
    cmd = " && ".join([
        "XJC=$$PWD/$(location //third_party/java/jaxb:jaxb-xjc)",
        "OUT=$$PWD/$(GENDIR)",
        "cp $(SRCS) /tmp",
        "cd /tmp",
        "$$XJC -npa -quiet -extension -d $$OUT/java -b *.xjb *.xsd",
        "find $$OUT/java -name '*.java' | xargs sed -i 's/" +
        "public class/" +
        "@SuppressWarnings({\"unchecked\",\"serial\",\"cast\"}) " +
        "public class/'",
    ]),
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
    tools = [
        ":jaxb_srcs",
        ":make_pkginfo",
    ],
)

java_library(
    name = "xjc",
    srcs = glob(["*.java"]) + xjc_generated_files + pkginfo_generated_files,
    deps = [
        "//java/com/google/common/collect",
        "//java/com/google/domain/registry/xml",
        "//third_party/java/joda_time",
        "//third_party/java/jsr305_annotations",
    ],
)
