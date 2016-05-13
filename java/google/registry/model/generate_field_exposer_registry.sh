#!/bin/sh -
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

# Generate a FieldExposer for a given package

printf "package com.google.domain.registry.model;\n\n\
import com.google.domain.registry.model.AbstractFieldExposer;\n\n\
import com.google.common.collect.ImmutableList;\n\n\
/** A registry of all {@link AbstractFieldExposer} impls. */\n\
class FieldExposerRegistry {\n\
  static ImmutableList<AbstractFieldExposer> getFieldExposers() {\n\
    return ImmutableList.of(\n"
for FILE in $@; do
  echo $FILE | sed \
      -e 's/\(.*\).java/        new com.google.domain.registry.model.\1()/' \
      -e 's/\//./g'
done
printf "  }\n}\n"
