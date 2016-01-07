// Copyright 2016 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.model;

import java.lang.reflect.Field;

/**
 * A helper that exposes package-private fields in this package for reflective lookup.
 *
 * <p>By adding a subclass of this to every package in the model, we can write generic code that can
 * access fields with package private access. The other alternative is to call
 * {@link Field#setAccessible} with {@code true} on any such Field objects, but that does not work
 * reliably in Google App Engine cross-package because of its custom security manager
 * implementation.
 */
public abstract class AbstractFieldExposer {
  public abstract Object getFieldValue(Object instance, Field field) throws IllegalAccessException;

  public abstract void setFieldValue(Object instance, Field field, Object value)
      throws IllegalAccessException;

  public abstract void setAccessible(Field field);
}
