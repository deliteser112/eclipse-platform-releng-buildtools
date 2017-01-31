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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.tools.ServerSideCommand.Connection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Unit tests for {@link DeleteEntityCommand}. */
public class DeleteEntityCommandTest extends CommandTestCase<DeleteEntityCommand> {

  @Mock
  private Connection connection;

  @Before
  public void init() {
    command.setConnection(connection);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_deleteTwoEntities() throws Exception {
    String firstKey = "alphaNumericKey1";
    String secondKey = "alphaNumericKey2";
    String rawKeys = String.format("%s,%s", firstKey, secondKey);
    when(connection.send(anyString(), anyMap(), any(MediaType.class), any(byte[].class)))
        .thenReturn("Deleted 1 raw entities and 1 registered entities.");
    runCommandForced(firstKey, secondKey);
    verify(connection).send(
        eq("/_dr/admin/deleteEntity"),
        eq(ImmutableMap.of("rawKeys", rawKeys)),
        eq(MediaType.PLAIN_TEXT_UTF_8),
        eq(new byte[0]));
    assertInStdout("Deleted 1 raw entities and 1 registered entities.");
  }
}
