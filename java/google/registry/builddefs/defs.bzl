# Copyright 2016 The Domain Registry Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Common routines for Domain Registry build rules."""

ZIPPER = "@bazel_tools//tools/zip:zipper"

_OUTPUT_DIRS = ("bazel-out/", "bazel-genfiles/")

def runpath(f):
  """Figures out the proper runfiles path for a file."""
  for prefix in _OUTPUT_DIRS:
    if f.path.startswith(prefix):
      return f.short_path
  return f.path
