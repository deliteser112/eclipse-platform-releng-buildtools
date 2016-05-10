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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.flows.EppServletUtils.APPLICATION_EPP_XML_UTF8;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static google.registry.xml.XmlTestUtils.assertXmlEquals;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.binarySearch;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;

import google.registry.testing.InjectRule;
import google.registry.tools.ServerSideCommand.Connection;

import org.junit.Before;
import org.junit.Rule;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.List;

/**
 * Abstract class for commands that construct + send EPP commands.
 *
 * @param <C> the command type
 */
public abstract class EppToolCommandTestCase<C extends EppToolCommand> extends CommandTestCase<C> {

  @Rule
  public InjectRule inject = new InjectRule();

  @Mock
  Connection connection;

  @Captor
  ArgumentCaptor<byte[]> xml;

  @Before
  public void init() throws Exception {
    // Create two TLDs for commands that allow multiple TLDs at once.
    createTlds("tld", "tld2");
    command.setConnection(connection);
    initEppToolCommandTestCase();
  }

  /** Subclasses can override this to perform additional initialization. */
  void initEppToolCommandTestCase() throws Exception {}

  /** Helper to get a new {@link EppVerifier} instance. */
  EppVerifier eppVerifier() {
    return new EppVerifier("NewRegistrar", false, false);
  }

  /** Class for verifying EPP commands sent to the server. */
  class EppVerifier {

    private final String clientIdentifier;
    private final boolean superuser;
    private final boolean dryRun;

    private EppVerifier(String clientIdentifier, boolean superuser, boolean dryRun) {
      this.clientIdentifier = clientIdentifier;
      this.superuser = superuser;
      this.dryRun = dryRun;
    }

    EppVerifier setClientIdentifier(String clientIdentifier) {
      return new EppVerifier(clientIdentifier, superuser, dryRun);
    }

    EppVerifier asSuperuser() {
      return new EppVerifier(clientIdentifier, true, dryRun);
    }

    EppVerifier asDryRun() {
      return new EppVerifier(clientIdentifier, superuser, true);
    }

    void verifySent(String... filesToMatch) throws Exception {
      ImmutableMap<String, ?> params = ImmutableMap.of(
          "clientIdentifier", clientIdentifier,
          "superuser", superuser,
          "dryRun", dryRun);
      verify(connection, times(filesToMatch.length))
          .send(eq("/_dr/epptool"), eq(params), eq(APPLICATION_EPP_XML_UTF8), xml.capture());
      List<byte[]> capturedXml = xml.getAllValues();
      assertThat(filesToMatch).hasLength(capturedXml.size());
      for (String fileToMatch : filesToMatch) {
        assertXmlEquals(
            readResourceUtf8(getClass(), fileToMatch),
            new String(capturedXml.get(binarySearch(filesToMatch, fileToMatch)), UTF_8));
      }
    }
  }
}
