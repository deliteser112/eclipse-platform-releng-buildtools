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

package google.registry.model.ofy;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.initOfy;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyFilter;
import com.googlecode.objectify.ObjectifyService;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.Registrar.Type;
import google.registry.testing.ExceptionRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for our replacement Objectify filter. */
@RunWith(JUnit4.class)
public class OfyFilterTest {

  private LocalServiceTestHelper helper;
  private ObjectifyFactory factory;

  // We can't use AppEngineRule, because it triggers the precise behavior that we are testing.

  @Before
  public void before() {
    helper = new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig()).setUp();
    // Clear out the factory so that it requires re-registration on each test method.
    // Otherwise, static registration of types in one method would persist across methods.
    initOfy();
    factory = ObjectifyService.factory();
    ObjectifyService.setFactory(new ObjectifyFactory(false));
  }

  @After
  public void after() {
    ObjectifyFilter.complete();
    ObjectifyService.setFactory(factory);
    ObjectifyFilter.complete();
    helper.tearDown();
  }

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  /**
   * Key.create looks up kind metadata for the class of the object it is given. If this happens
   * before the first reference to ObjectifyService, which statically triggers type registrations,
   * then the create will fail. Note that this is only a problem if the type in question doesn't
   * call ObjectifyService.allocateId() inside its own builder or create method, since if it
   * does that would trigger the statics as well. In this example, Registrar has a string id, so
   * the bug occurs, were it not for OfyFilter.
   */
  @Test
  public void testFilterRegistersTypes() throws Exception {
    Registrar registrar = new Registrar.Builder()
        .setType(Type.TEST)
        .setClientIdentifier("registrar")
        .build();
    try {
      Key.create(registrar);
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage(
          "class google.registry.model.registrar.Registrar has not been registered");
    }
  }

  /** The filter should register all types for us. */
  @Test
  public void testKeyCreateAfterFilter() throws Exception {
    new OfyFilter().init(null);
    Registrar registrar = new Registrar.Builder()
        .setType(Type.TEST)
        .setClientIdentifier("registrar")
        .build();
    Key.create(registrar);
  }
}
