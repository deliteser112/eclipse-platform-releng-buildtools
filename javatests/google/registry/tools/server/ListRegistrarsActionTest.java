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

package google.registry.tools.server;

import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.Registrar;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ListRegistrarsAction}.
 */
@RunWith(JUnit4.class)
public class ListRegistrarsActionTest extends ListActionTestCase {

  ListRegistrarsAction action;

  @Before
  public void init() throws Exception {
    action = new ListRegistrarsAction();
    createTlds("xn--q9jyb4c", "example");
    // Ensure that NewRegistrar only has access to xn--q9jyb4c and that TheRegistrar only has access
    // to example.
    persistResource(
        Registrar.loadByClientId("NewRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("xn--q9jyb4c"))
            .build());
    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("example"))
            .build());
  }

  @Test
  public void testRun_noParameters() throws Exception {
    testRunSuccess(
        action,
        null,
        null,
        null,
        "^NewRegistrar$",
        "^TheRegistrar$");
  }

  @Test
  public void testRun_withParameters() throws Exception {
    testRunSuccess(
        action,
        Optional.of("allowedTlds"),
        null,
        null,
        "^clientId\\s+allowedTlds\\s*$",
        "-+\\s+-+\\s*$",
        "^NewRegistrar\\s+\\[xn--q9jyb4c\\]\\s*$",
        "^TheRegistrar\\s+\\[example\\]\\s*$");
  }

  @Test
  public void testRun_withWildcard() throws Exception {
    testRunSuccess(
        action,
        Optional.of("*"),
        null,
        null,
        "^clientId\\s+.*allowedTlds",
        "^-+\\s+-+",
        "^NewRegistrar\\s+.*\\[xn--q9jyb4c\\]",
        "^TheRegistrar\\s+.*\\[example\\]");
  }

  @Test
  public void testRun_withBadField_returnsError() throws Exception {
    testRunError(
        action,
        Optional.of("badfield"),
        null,
        null,
        "^Field 'badfield' not found - recognized fields are:");
  }
}
