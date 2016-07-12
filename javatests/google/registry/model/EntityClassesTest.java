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

package google.registry.model;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EntityClasses.ALL_CLASSES;
import static google.registry.model.EntityClasses.CLASS_TO_KIND_FUNCTION;
import static google.registry.util.TypeUtils.hasAnnotation;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.EntitySubclass;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link EntityClasses}. */
@RunWith(JUnit4.class)
public class EntityClassesTest {

  // This implements the manual ordering we've been using for the EntityClasses class lists.
  private static final Ordering<Class<?>> QUALIFIED_CLASS_NAME_ORDERING =
      Ordering.natural().onResultOf(new Function<Class<?>, String>() {
        @Override
        public String apply(Class<?> clazz) {
          // Return only the part of the class name after the package name, which is the class
          // name plus any outer class names.
          return clazz.getCanonicalName().substring(clazz.getPackage().getName().length());
        }
      });

  @Test
  public void testEntityClasses_inAlphabeticalOrder() throws Exception {
    assertThat(ALL_CLASSES).isStrictlyOrdered(QUALIFIED_CLASS_NAME_ORDERING);
  }

  @Test
  public void testEntityClasses_baseEntitiesHaveUniqueKinds() throws Exception {
    assertThat(FluentIterable.from(ALL_CLASSES)
        .filter(hasAnnotation(Entity.class))
        .transform(CLASS_TO_KIND_FUNCTION))
            .named("base entity kinds")
            .containsNoDuplicates();
  }

  @Test
  public void testEntityClasses_entitySubclassesHaveKindsMatchingBaseEntities() throws Exception {
    Set<String> baseEntityKinds = FluentIterable.from(ALL_CLASSES)
        .filter(hasAnnotation(Entity.class))
        .transform(CLASS_TO_KIND_FUNCTION)
        .toSet();
    Set<String> entitySubclassKinds = FluentIterable.from(ALL_CLASSES)
        .filter(hasAnnotation(EntitySubclass.class))
        .transform(CLASS_TO_KIND_FUNCTION)
        .toSet();
    assertThat(baseEntityKinds).named("base entity kinds").containsAllIn(entitySubclassKinds);
  }

  @Test
  public void testEntityClasses_eitherBaseEntityOrEntitySubclass() throws Exception {
    for (Class<?> clazz : ALL_CLASSES) {
      boolean isEntityXorEntitySubclass =
          clazz.isAnnotationPresent(Entity.class) ^ clazz.isAnnotationPresent(EntitySubclass.class);
      assertThat(isEntityXorEntitySubclass)
          .named("class " + clazz.getSimpleName() + " is @Entity or @EntitySubclass")
          .isTrue();
    }
  }
}
