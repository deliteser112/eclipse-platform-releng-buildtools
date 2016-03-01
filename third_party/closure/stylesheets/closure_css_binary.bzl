# -*- mode: python; -*-
#
# Copyright 2016 Google Inc. All rights reserved.
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

"""Build definitions for CSS compiled by the Closure Stylesheets.
"""

_CSS_FILE_TYPE = FileType([".css", ".gss"])
_JS_FILE_TYPE = FileType([".js"])

def _impl(ctx):
  srcs = set(order="compile")
  for dep in ctx.attr.deps:
    srcs += dep.transitive_css_srcs
  srcs += _CSS_FILE_TYPE.filter(ctx.files.srcs)
  js_srcs = set(order="compile")
  js_srcs += ctx.attr._library.transitive_js_srcs
  js_srcs += _JS_FILE_TYPE.filter([ctx.outputs.js])
  js_externs = set(order="compile")
  js_externs += ctx.attr._library.transitive_js_externs
  args = [
      "--output-file",
      ctx.outputs.out.path,
      "--output-renaming-map",
      ctx.outputs.js.path,
      "--output-renaming-map-format",
      "CLOSURE_COMPILED_SPLIT_HYPHENS",
  ]
  if ctx.attr.debug:
    args += ["--rename", "DEBUG", "--pretty-print"]
  else:
    args += ["--rename", "CLOSURE"]
  args += ctx.attr.defs
  args += [src.path for src in srcs]
  ctx.action(
      inputs=list(srcs),
      outputs=[ctx.outputs.out, ctx.outputs.js],
      arguments=args,
      executable=ctx.executable._compiler,
      progress_message="Compiling %d stylesheets to %s" % (
          len(srcs), ctx.outputs.out.short_path))
  return struct(files=set([ctx.outputs.out]),
                js_language="ANY",
                js_exports=set(order="compile"),
                transitive_js_srcs=js_srcs,
                transitive_js_externs=js_externs)

closure_css_binary = rule(
    implementation=_impl,
    attrs={
        "srcs": attr.label_list(allow_files=_CSS_FILE_TYPE),
        "deps": attr.label_list(
            allow_files=False,
            providers=["transitive_css_srcs"]),
        "defs": attr.string_list(),
        "debug": attr.bool(default=False),
        "_compiler": attr.label(
            default=Label("//third_party/closure/stylesheets"),
            executable=True),
        "_library": attr.label(
            default=Label("//third_party/closure/library"),
            providers=["transitive_js_srcs",
                       "transitive_js_externs"]),
    },
    outputs={"out": "%{name}.css",
             "js": "%{name}.css.js"})
