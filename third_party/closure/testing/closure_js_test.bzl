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

"""Build rule for running Closure Library JsUnit tests in PhantomJS.
"""

# XXX: It would be significantly faster and produce better stacktraces if we
#      could avoid compilation by running in raw sources mode. This is not
#      possible due to a resource loading bug in PhantomJS.
#      https://github.com/ariya/phantomjs/issues/14028

load("//third_party/closure/compiler/private:defs.bzl",
     "JS_HIDE_WARNING_ARGS",
     "JS_LANGUAGE_DEFAULT",
     "JS_LIBRARY_ATTRS",
     "JS_PEDANTIC_ARGS",
     "collect_js_srcs",
     "determine_js_language")

def _impl(ctx):
  srcs, externs = collect_js_srcs(ctx)
  srcs += [ctx.file._phantomjs_jsunit_runner]
  args = [
      "--js_output_file=%s" % ctx.outputs.js.path,
      "--language_in=%s" % determine_js_language(ctx),
      "--language_out=ECMASCRIPT5_STRICT",
      "--compilation_level=WHITESPACE_ONLY",
      "--warning_level=VERBOSE",
      "--dependency_mode=LOOSE",
      "--formatting=PRETTY_PRINT",
      "--new_type_inf",
      "--debug",
  ]
  if ctx.attr.pedantic:
    args += JS_PEDANTIC_ARGS
  args += JS_HIDE_WARNING_ARGS
  args += ["--externs=%s" % extern.path for extern in externs]
  args += ["--js=%s" % src.path for src in srcs]
  ctx.action(
      inputs=list(srcs) + list(externs),
      outputs=[ctx.outputs.js],
      executable=ctx.executable._compiler,
      arguments=args,
      mnemonic="JSCompile",
      progress_message="Compiling %d JavaScript files to %s" % (
          len(srcs) + len(externs), ctx.outputs.js.short_path))
  ctx.file_action(
      executable=True,
      output=ctx.outputs.executable,
      content="\n".join([
          "#!/bin/sh",
          "exec %s \\\n  %s \\\n  %s\n" % (
              ctx.file._phantomjs.short_path,
              ctx.file._phantomjs_runner.short_path,
              ctx.outputs.js.short_path),
      ]))
  return struct(
      files=set([ctx.outputs.executable,
                 ctx.outputs.js]),
      runfiles=ctx.runfiles(files=[ctx.file._phantomjs,
                                   ctx.file._phantomjs_runner,
                                   ctx.outputs.js],
                            collect_data=True))

_closure_js_test = rule(
    test=True,
    implementation=_impl,
    attrs=JS_LIBRARY_ATTRS + {
        "language": attr.string(default=JS_LANGUAGE_DEFAULT),
        "pedantic": attr.bool(default=False),
        "_compiler": attr.label(
            default=Label("//third_party/closure/compiler"),
            executable=True),
        "_phantomjs": attr.label(
            default=Label("//third_party/phantomjs"),
            allow_files=True,
            single_file=True),
        "_phantomjs_runner": attr.label(
            default=Label("//third_party/closure/testing:phantomjs_runner.js"),
            allow_files=True,
            single_file=True),
        "_phantomjs_jsunit_runner": attr.label(
            default=Label(
                "//third_party/closure/testing:phantomjs_jsunit_runner.js"),
            allow_files=True,
            single_file=True),
    },
    outputs={"js": "%{name}_dbg.js"})

# XXX: In compiled mode, we're forced to compile each test file individually,
#      because tests might have overlapping global symbols. We compile in
#      WHITESPACE_ONLY mode because other modes would be unreasonably slow.

def closure_js_test(name, srcs, **kwargs):
  if len(srcs) == 1:
    _closure_js_test(name = name, srcs = srcs, **kwargs)
  else:
    tests = []
    for src in srcs:
      test = name + '_' + src.replace('_test.js', '').replace('-', '_')
      tests += [test]
      _closure_js_test(name = test, srcs = [src], **kwargs)
    native.test_suite(name = name, tests = tests)
