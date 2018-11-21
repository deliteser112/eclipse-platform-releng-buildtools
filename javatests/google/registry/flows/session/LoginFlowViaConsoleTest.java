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

package google.registry.flows.session;


import com.google.common.collect.ImmutableSetMultimap;
import google.registry.flows.GaeUserCredentials;
import google.registry.flows.GaeUserCredentials.UserForbiddenException;
import google.registry.request.auth.AuthenticatedRegistrarAccessor;
import org.junit.Test;

/**
 * Unit tests for {@link LoginFlow} when accessed via a web frontend
 * transport, i.e. with GAIA ids.
 */
public class LoginFlowViaConsoleTest extends LoginFlowTestCase {

  @Test
  public void testSuccess_withAccess() throws Exception {
    credentials =
        new GaeUserCredentials(
            AuthenticatedRegistrarAccessor.createForTesting(
                ImmutableSetMultimap.of(
                    "NewRegistrar", AuthenticatedRegistrarAccessor.Role.OWNER)));
    doSuccessfulTest("login_valid.xml");
  }

  @Test
  public void testFailure_withoutAccess() {
    credentials =
        new GaeUserCredentials(
            AuthenticatedRegistrarAccessor.createForTesting(
                ImmutableSetMultimap.of()));
    doFailingTest("login_valid.xml", UserForbiddenException.class);
  }

  @Test
  public void testFailure_withAccessToDifferentRegistrar() {
    credentials =
        new GaeUserCredentials(
            AuthenticatedRegistrarAccessor.createForTesting(
                ImmutableSetMultimap.of(
                    "TheRegistrar", AuthenticatedRegistrarAccessor.Role.OWNER)));
    doFailingTest("login_valid.xml", UserForbiddenException.class);
  }
}
