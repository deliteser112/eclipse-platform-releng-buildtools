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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.JUnitBackports.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.tools.CommandWithConnection.Connection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

/** Unit tests for {@link RefreshDnsForAllDomainsCommand}. */
public class CurlCommandTest extends CommandTestCase<CurlCommand> {
  @Mock private Connection connection;

  @Before
  public void init() {
    command.setConnection(connection);
  }

  @Captor ArgumentCaptor<ImmutableMap<String, String>> urlParamCaptor;

  @Test
  public void testGetInvocation() throws Exception {
    runCommand("--path=/foo/bar?a=1&b=2");
    verify(connection)
        .sendGetRequest(eq("/foo/bar?a=1&b=2"), eq(ImmutableMap.<String, String>of()));
  }

  @Test
  public void testExplicitGetInvocation() throws Exception {
    runCommand("--path=/foo/bar?a=1&b=2", "--request=GET");
    verify(connection)
        .sendGetRequest(eq("/foo/bar?a=1&b=2"), eq(ImmutableMap.<String, String>of()));
  }

  @Test
  public void testPostInvocation() throws Exception {
    runCommand("--path=/foo/bar?a=1&b=2", "--data=some data");
    verify(connection)
        .send(
            eq("/foo/bar?a=1&b=2"),
            eq(ImmutableMap.<String, String>of()),
            eq(MediaType.PLAIN_TEXT_UTF_8),
            eq("some data".getBytes(UTF_8)));
  }

  @Test
  public void testMultiDataPost() throws Exception {
    runCommand("--path=/foo/bar?a=1&b=2", "--data=first=100", "-d", "second=200");
    verify(connection)
        .send(
            eq("/foo/bar?a=1&b=2"),
            eq(ImmutableMap.<String, String>of()),
            eq(MediaType.PLAIN_TEXT_UTF_8),
            eq("first=100&second=200".getBytes(UTF_8)));
  }

  @Test
  public void testExplicitPostInvocation() throws Exception {
    runCommand("--path=/foo/bar?a=1&b=2", "--request=POST");
    verify(connection)
        .send(
            eq("/foo/bar?a=1&b=2"),
            eq(ImmutableMap.<String, String>of()),
            eq(MediaType.PLAIN_TEXT_UTF_8),
            eq("".getBytes(UTF_8)));
  }

  @Test
  public void testGetWithBody() throws Exception {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommand(
                    "--path=/foo/bar?a=1&b=2", "--request=GET", "--data=inappropriate data"));
    assertThat(thrown).hasMessageThat().contains("You may not specify a body for a get method.");
  }
}
