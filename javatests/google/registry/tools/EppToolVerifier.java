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
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static google.registry.xml.XmlTestUtils.assertXmlEquals;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.tools.ServerSideCommand.Connection;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;

/** Class for verifying EPP commands sent to the server via the tool endpoint. */
public class EppToolVerifier {

  private final Connection connection;
  private final String clientId;
  private final boolean superuser;
  private final boolean dryRun;

  public EppToolVerifier() {
    this(null, null, false, false);
  }

  private EppToolVerifier(
      Connection connection, String clientId, boolean superuser, boolean dryRun) {
    this.connection = connection;
    this.clientId = clientId;
    this.superuser = superuser;
    this.dryRun = dryRun;
  }

  EppToolVerifier withConnection(Connection connection) {
    return new EppToolVerifier(connection, clientId, superuser, dryRun);
  }

  EppToolVerifier withClientId(String clientId) {
    return new EppToolVerifier(connection, clientId, superuser, dryRun);
  }

  EppToolVerifier asSuperuser() {
    return new EppToolVerifier(connection, clientId, true, dryRun);
  }

  EppToolVerifier asDryRun() {
    return new EppToolVerifier(connection, clientId, superuser, true);
  }

  void verifySent(String... xmlToMatch) throws Exception {
    ArgumentCaptor<byte[]> params = ArgumentCaptor.forClass(byte[].class);
    verify(connection, times(xmlToMatch.length)).send(
        eq("/_dr/epptool"),
        eq(ImmutableMap.<String, Object>of()),
        eq(MediaType.FORM_DATA),
        params.capture());
    List<byte[]> capturedParams = params.getAllValues();
    assertThat(capturedParams).hasSize(xmlToMatch.length);
    for (int i = 0; i < xmlToMatch.length; i++) {
      String xml = xmlToMatch[i];
      byte[] capturedParam = capturedParams.get(i);
      Map<String, String> map =
          Splitter.on('&')
              .withKeyValueSeparator('=')
              .split(new String(capturedParam, UTF_8));
      assertThat(map).hasSize(4);
      assertXmlEquals(
          readResourceUtf8(getClass(), "testdata/" + xml),
          URLDecoder.decode(map.get("xml"), UTF_8.toString()));
      assertThat(map).containsEntry("dryRun", Boolean.toString(dryRun));
      assertThat(map).containsEntry("clientId", clientId);
      assertThat(map).containsEntry("superuser", Boolean.toString(superuser));
    }
  }
}
