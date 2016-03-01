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

"""Build definitions for Closure JavaScript libraries.
"""

load("//third_party/closure/compiler/private:defs.bzl",
     "JS_LANGUAGE_DEFAULT",
     "JS_DEPS_ATTR",
     "JS_LIBRARY_ATTRS",
     "collect_js_srcs",
     "determine_js_language")

def _impl(ctx):
  srcs, externs = collect_js_srcs(ctx)
  return struct(files=set(ctx.files.srcs),
                js_language=determine_js_language(ctx),
                js_exports=ctx.files.exports,
                transitive_js_srcs=srcs,
                transitive_js_externs=externs)

closure_js_library = rule(
    implementation=_impl,
    attrs=JS_LIBRARY_ATTRS + {
        "exports": JS_DEPS_ATTR,
        "language": attr.string(default=JS_LANGUAGE_DEFAULT),
    })
