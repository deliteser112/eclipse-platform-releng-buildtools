// Copyright 2019 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools;

import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.testing.JUnitBackports.assertThrows;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import google.registry.model.domain.token.AllocationToken;
import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link GetAllocationTokenCommand}. */
public class GetAllocationTokenCommandTest extends CommandTestCase<GetAllocationTokenCommand> {

  @Test
  public void testSuccess_oneToken() throws Exception {
    AllocationToken token =
        persistResource(
            new AllocationToken.Builder().setToken("foo").setDomainName("foo.bar").build());
    runCommand("foo");
    assertInStdout(token.toHydratedString());
  }

  @Test
  public void testSuccess_multipleTokens() throws Exception {
    ImmutableList<AllocationToken> tokens = persistSimpleResources(
        ImmutableList.of(
            new AllocationToken.Builder()
                .setToken("fee")
                .setCreationTimeForTest(DateTime.parse("2015-04-07T22:19:17.044Z"))
                .build(),
            new AllocationToken.Builder().setToken("fii").setDomainName("bar.baz").build()));
    runCommand("fee", "fii");
    assertInStdout(tokens.get(0).toHydratedString(), tokens.get(1).toHydratedString());
  }

  @Test
  public void testSuccess_oneTokenDoesNotExist() throws Exception {
    AllocationToken token =
        persistResource(
            new AllocationToken.Builder().setToken("foo").setDomainName("foo.bar").build());
    runCommand("foo", "bar");
    assertInStdout(token.toHydratedString(), "Token bar does not exist.");
  }

  @Test
  public void testFailure_noAllocationTokensSpecified() {
    assertThrows(ParameterException.class, this::runCommand);
  }
}
