# -*- mode:python; -*-
#
# Copyright 2016 The Domain Registry Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Utilities for compiling Closure Templates to JavaScript.
"""

load("//third_party/closure/compiler:closure_js_library.bzl",
     "closure_js_library")

def closure_template_js_library(
    name,
    srcs,
    deps = [],
    testonly = 0,
    visibility = None,
    globals = None,
    plugin_modules = [],
    should_generate_js_doc = 1,
    should_provide_require_soy_namespaces = 1,
    should_generate_soy_msg_defs = 0,
    soy_msgs_are_external = 0,
    soycompilerbin = "//third_party/closure/templates:SoyToJsSrcCompiler"):
  js_srcs = [src + ".js" for src in srcs]
  cmd = ["$(location %s)" % soycompilerbin,
         "--outputPathFormat='$(@D)/{INPUT_FILE_NAME}.js'"]
  if soy_msgs_are_external:
    cmd += ["--googMsgsAreExternal"]
  if should_provide_require_soy_namespaces:
    cmd += ["--shouldProvideRequireSoyNamespaces"]
  if should_generate_soy_msg_defs:
    cmd += "--shouldGenerateGoogMsgDefs"
  if plugin_modules:
    cmd += ["--pluginModules=%s" % ",".join(plugin_modules)]
  cmd += ["$(location " + src + ")" for src in srcs]
  if globals != None:
    cmd += ["--compileTimeGlobalsFile='$(location %s)'" % globals]
    srcs = srcs + [globals]

  native.genrule(
      name = name + "_soy_js",
      srcs = srcs,
      testonly = testonly,
      visibility = visibility,
      message = "Generating SOY v2 JS files",
      outs = js_srcs,
      tools = [soycompilerbin],
      cmd = " ".join(cmd),
  )

  closure_js_library(
      name = name,
      srcs = js_srcs,
      deps = deps + ["//third_party/closure/templates:soyutils_usegoog"],
  )
