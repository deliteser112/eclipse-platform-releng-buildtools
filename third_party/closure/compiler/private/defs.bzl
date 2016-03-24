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

"""Common build definitions for Closure Compiler build definitions.
"""

JS_LANGUAGE_DEFAULT = "ECMASCRIPT6_STRICT"
JS_FILE_TYPE = FileType([".js"])
JS_TEST_FILE_TYPE = FileType(["_test.js"])
_CLOSURE_ROOT = "external/closure_library/closure/goog"
_CLOSURE_REL = "../../../.."

JS_LANGUAGES = set([
    "ANY",
    "ECMASCRIPT3",
    "ECMASCRIPT5",
    "ECMASCRIPT5_STRICT",
    "ECMASCRIPT6",
    "ECMASCRIPT6_STRICT",
    "ECMASCRIPT6_TYPED",
])

JS_DEPS_ATTR = attr.label_list(
    allow_files=False,
    providers=["js_language",
               "js_exports",
               "transitive_js_srcs",
               "transitive_js_externs"])

JS_LIBRARY_ATTRS = {
    "srcs": attr.label_list(allow_files=JS_FILE_TYPE),
    "externs_list": attr.label_list(allow_files=JS_FILE_TYPE),
    "deps": JS_DEPS_ATTR,
}

JS_PEDANTIC_ARGS = [
    "--jscomp_error=*",
    "--jscomp_warning=deprecated",
    "--jscomp_warning=unnecessaryCasts",
]

JS_HIDE_WARNING_ARGS = [
    "--hide_warnings_for=.soy.js",
    "--hide_warnings_for=external/closure_library/",
    "--hide_warnings_for=external/soyutils_usegoog/",
]

def collect_js_srcs(ctx):
  srcs = set(order="compile")
  externs = set(order="compile")
  for dep in ctx.attr.deps:
    srcs += dep.js_exports
    srcs += dep.transitive_js_srcs
    externs += dep.transitive_js_externs
  srcs += JS_FILE_TYPE.filter(ctx.files.srcs)
  externs += JS_FILE_TYPE.filter(ctx.files.externs_list)
  return srcs, externs

def check_js_language(language):
  if language not in JS_LANGUAGES:
    fail("Invalid JS language '%s', expected one of %s" % (
        language, ', '.join(list(JS_LANGUAGES))))
  return language

def determine_js_language(ctx):
  language = None
  if hasattr(ctx.attr, 'language'):
    language = check_js_language(ctx.attr.language)
  for dep in ctx.attr.deps:
    language = _mix_js_languages(language, dep.js_language)
  if hasattr(ctx.attr, 'exports'):
    for dep in ctx.attr.deps:
      language = _mix_js_languages(language, dep.js_language)
  return language or JS_LANGUAGE_DEFAULT

def make_js_deps_runfiles(ctx, srcs):
  ctx.action(
      inputs=list(srcs),
      outputs=[ctx.outputs.runfiles],
      arguments=(["--output_file=%s" % ctx.outputs.runfiles.path] +
                 ["--root_with_prefix=%s %s" % (r, _make_prefix(p))
                  for r, p in _find_roots(
                      [(src.dirname, src.short_path) for src in srcs])]),
      executable=ctx.executable._depswriter,
      progress_message="Calculating %d JavaScript runfile deps to %s" % (
          len(srcs), ctx.outputs.runfiles.short_path))

def is_using_closure_library(srcs):
  return _contains_file(srcs, "external/closure_library/closure/goog/base.js")

_JS_LANGUAGE_COMPATIBILITY = set([
    ("ECMASCRIPT5", "ECMASCRIPT3"),
    ("ECMASCRIPT5", "ECMASCRIPT5_STRICT"),
    ("ECMASCRIPT6", "ECMASCRIPT3"),
    ("ECMASCRIPT6", "ECMASCRIPT5"),
    ("ECMASCRIPT6", "ECMASCRIPT5_STRICT"),
    ("ECMASCRIPT6", "ECMASCRIPT6_STRICT"),
    ("ECMASCRIPT6_STRICT", "ECMASCRIPT5_STRICT"),
    ("ECMASCRIPT6_TYPED", "ECMASCRIPT6_STRICT"),
    ("ECMASCRIPT6_TYPED", "ECMASCRIPT5_STRICT"),
])

_JS_LANGUAGE_DECAY = {
    ("ECMASCRIPT5_STRICT", "ECMASCRIPT3"): "ECMASCRIPT5",
    ("ECMASCRIPT5_STRICT", "ECMASCRIPT5"): "ECMASCRIPT5",
    ("ECMASCRIPT6_STRICT", "ECMASCRIPT3"): "ECMASCRIPT6",
    ("ECMASCRIPT6_STRICT", "ECMASCRIPT5"): "ECMASCRIPT6",
    ("ECMASCRIPT6_STRICT", "ECMASCRIPT6"): "ECMASCRIPT6",
}

def _mix_js_languages(current, dependent):
  if not current:
    return dependent
  if current == dependent:
    return current
  if current == "ANY":
    return dependent
  if dependent == "ANY":
    return current
  if (current, dependent) in _JS_LANGUAGE_COMPATIBILITY:
    return current
  decay = _JS_LANGUAGE_DECAY[(current, dependent)]
  if decay:
    print("Dependency causing JS strictness to decay from %s to %s :(" % (
        current, decay))
    return dependent
  decay = _JS_LANGUAGE_DECAY[(dependent, current)]
  if decay:
    return dependent
  fail("Can not link an %s library against an %s one." % (dependent, current))

def _find_roots(dirs):
  roots = {}
  for _, d, p in sorted([(len(d.split("/")), d, p) for d, p in dirs]):
    parts = d.split("/")
    want = True
    for i in range(len(parts)):
      if "/".join(parts[:i + 1]) in roots:
        want = False
        break
    if want:
      roots[d] = p
  return roots.items()

def _make_prefix(prefix):
  prefix = "/".join(prefix.split("/")[:-1])
  if not prefix:
    return _CLOSURE_REL
  elif prefix == _CLOSURE_ROOT:
    return "."
  elif prefix.startswith(_CLOSURE_ROOT + "/"):
    return prefix[len(_CLOSURE_ROOT) + 1:]
  else:
    return _CLOSURE_REL + "/" + prefix

def _contains_file(srcs, path):
  for src in srcs:
    if src.short_path == path:
      return True
  return False
