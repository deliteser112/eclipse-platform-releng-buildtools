# -*- mode: python; -*-
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

"""Build definitions for JavaScript dependency files.

Generating this file is important because the deps-runfiles.js file tells the
Closure Library how to send requests to the web server to load goog.require'd
namespaces.
"""

load("//third_party/closure/compiler/private:defs.bzl",
     "JS_FILE_TYPE",
     "make_js_deps_runfiles")

def _impl(ctx):
  srcs = set(order="compile")
  for src in ctx.attr.srcs:
    srcs += src.transitive_js_srcs
  ctx.action(
      inputs=list(srcs),
      outputs=[ctx.outputs.out],
      arguments=(["--output_file=%s" % (ctx.outputs.out.path)] +
                 [src.path for src in srcs]),
      executable=ctx.executable._depswriter,
      progress_message="Calculating %d JavaScript deps to %s" % (
          len(srcs), ctx.outputs.out.short_path))
  make_js_deps_runfiles(ctx, srcs)
  return struct(files=set([ctx.outputs.out, ctx.outputs.runfiles]))

closure_js_deps = rule(
    implementation=_impl,
    attrs={
        "srcs": attr.label_list(
            allow_files=False,
            providers=["transitive_js_srcs"]),
        "_depswriter": attr.label(
            default=Label("@closure_library//:depswriter"),
            executable=True),
    },
    outputs={"out": "%{name}.js",
             "runfiles": "%{name}-runfiles.js"})
