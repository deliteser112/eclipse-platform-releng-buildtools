#!/bin/sh -
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


# Generate a FieldExposer for a given package

printf "package google.registry.model"\
`echo $1 | sed -e 's/.*\/model\(.*\).FieldExposer\.java/\1/' -e 's/\//./g'`";\
\n\n\
import google.registry.model.AbstractFieldExposer;\n\n\
import java.lang.reflect.Field;\n\n\
/** A helper that exposes package-private fields in this package for reflective lookup. */\n\
public class FieldExposer extends AbstractFieldExposer {\n\
  public Object getFieldValue(Object instance, Field field) throws IllegalAccessException {\n\
    return field.get(instance);\n\
  }\n\n\
  public void setFieldValue(Object instance, Field field, Object value)\n\
      throws IllegalAccessException {\n\
    field.set(instance, value);\n\
  }\n\n\
  public void setAccessible(Field field) {\n\
    field.setAccessible(true);\n\
  }\n\
}\n"
