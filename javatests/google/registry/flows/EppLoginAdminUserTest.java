// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.flows;

import com.google.common.collect.ImmutableSetMultimap;
import google.registry.request.auth.AuthenticatedRegistrarAccessor;
import google.registry.testing.AppEngineRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test logging in with appengine admin user credentials. */
@RunWith(JUnit4.class)
public class EppLoginAdminUserTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Before
  public void initTransportCredentials() {
    setTransportCredentials(
        new GaeUserCredentials(
            AuthenticatedRegistrarAccessor.createForTesting(
                ImmutableSetMultimap.of(
                    "TheRegistrar", AuthenticatedRegistrarAccessor.Role.ADMIN,
                    "NewRegistrar", AuthenticatedRegistrarAccessor.Role.ADMIN))));
  }

  @Test
  public void testLoginLogout_wrongPasswordStillWorks() throws Exception {
    // For user-based logins the password in the epp xml is ignored.
    assertThatLoginSucceeds("NewRegistrar", "incorrect");
    assertThatLogoutSucceeds();
  }

  @Test
  public void testNonAuthedMultiLogin_succeedsAsAdmin() throws Exception {
    // The admin can log in as different registrars.
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    assertThatLogoutSucceeds();
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    assertThatLogoutSucceeds();
    assertThatLoginSucceeds("TheRegistrar", "password2");
  }
}
