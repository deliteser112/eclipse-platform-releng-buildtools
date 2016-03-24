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

"""Build definitions for Closure Stylesheet libraries.
"""

_CSS_FILE_TYPE = FileType([".css", ".gss"])

def _impl(ctx):
  srcs = set(order="compile")
  for dep in ctx.attr.deps:
    srcs += dep.transitive_css_srcs
  srcs += _CSS_FILE_TYPE.filter(ctx.files.srcs)
  return struct(files=set(), transitive_css_srcs=srcs)

closure_css_library = rule(
    implementation=_impl,
    attrs={
        "srcs": attr.label_list(allow_files=_CSS_FILE_TYPE),
        "deps": attr.label_list(providers=["transitive_css_srcs"])
    })
