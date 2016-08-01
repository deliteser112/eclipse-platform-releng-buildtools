// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import javax.annotation.concurrent.ThreadSafe;

/**
 * System property loader of {@link RegistryConfig} instance.
 *
 * <p>This class reflectively loads the Java class defined by the system property
 * {@value #REGISTRY_CONFIG_PROPERTY} whose default value is {@value #REGISTRY_CONFIG_DEFAULT} and
 * can be set in {@code appengine-web.xml}. Once the class is loaded, its constructor is called,
 * passing the {@link RegistryEnvironment} as a single parameter.
 */
@ThreadSafe
public final class RegistryConfigLoader {

  public static final String REGISTRY_CONFIG_PROPERTY = "com.google.domain.registry.config";
  public static final String REGISTRY_CONFIG_DEFAULT =
      "google.registry.config.ProductionRegistryConfigExample";

  static RegistryConfig load(RegistryEnvironment environment) {
    String className = System.getProperty(REGISTRY_CONFIG_PROPERTY, REGISTRY_CONFIG_DEFAULT);
    Class<?> clazz;
    try {
      clazz = Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(String.format(
          "Failed to load '%s' as specified by system property '%s'",
          className, REGISTRY_CONFIG_PROPERTY), e);
    }
    if (!RegistryConfig.class.isAssignableFrom(clazz)) {
      throw new RuntimeException(String.format(
          "%s does not implement %s",
          clazz.getSimpleName(), RegistryConfig.class.getSimpleName()));
    }
    @SuppressWarnings("unchecked")
    Class<? extends RegistryConfig> clazzy = (Class<? extends RegistryConfig>) clazz;
    if (!Modifier.isPublic(clazzy.getModifiers())) {
      throw new RuntimeException(String.format(
          "Must be a public class: %s", clazzy.getCanonicalName()));
    }
    Constructor<? extends RegistryConfig> constructor;
    try {
      constructor = clazzy.getConstructor(RegistryEnvironment.class);
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(String.format(
          "Must have a public constructor(RegistryEnvironment): %s", clazzy.getCanonicalName()), e);
    }
    try {
      return constructor.newInstance(environment);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(
          String.format("%s constructor threw an exception", clazzy.getSimpleName()), e);
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
      throw new RuntimeException(
          String.format("Failed to instantiate: %s", clazz.getCanonicalName()), e);
    }
  }
}
