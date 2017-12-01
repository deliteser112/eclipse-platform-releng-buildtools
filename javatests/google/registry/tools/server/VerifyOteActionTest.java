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

package google.registry.tools.server;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.deleteResource;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.reporting.HistoryEntry.Type;
import google.registry.testing.AppEngineRule;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link VerifyOteAction}. */
@RunWith(JUnit4.class)
public class VerifyOteActionTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  private final VerifyOteAction action = new VerifyOteAction();

  HistoryEntry hostDeleteHistoryEntry;

  @Before
  public void init() throws Exception {
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_APPLICATION_CREATE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_create_complete.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_APPLICATION_CREATE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_create_sunrise.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_APPLICATION_DELETE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_delete.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-2")
            .setType(Type.DOMAIN_APPLICATION_DELETE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_delete.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_APPLICATION_UPDATE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_update_complete.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-2")
            .setType(Type.DOMAIN_APPLICATION_UPDATE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_update_complete.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_CREATE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_create_idn.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_CREATE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_create_claim_notice.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_CREATE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_create_anchor_tenant_fee_standard.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_CREATE)
            .setXmlBytes(ToolsTestData.loadBytes("allocate_domain.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_DELETE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_delete.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-2")
            .setType(Type.DOMAIN_DELETE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_delete.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_RESTORE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_restore.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_TRANSFER_APPROVE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_transfer_approve.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_TRANSFER_CANCEL)
            .setXmlBytes(ToolsTestData.loadBytes("domain_transfer_cancel.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_TRANSFER_REJECT)
            .setXmlBytes(ToolsTestData.loadBytes("domain_transfer_reject.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_TRANSFER_REQUEST)
            .setXmlBytes(ToolsTestData.loadBytes("domain_transfer_request.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.DOMAIN_UPDATE)
            .setXmlBytes(ToolsTestData.loadBytes("domain_update_with_secdns.xml").read())
            .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.HOST_CREATE)
            .setXmlBytes(ToolsTestData.loadBytes("host_create_complete.xml").read())
            .build());
    hostDeleteHistoryEntry =
        persistResource(
            new HistoryEntry.Builder()
                .setClientId("blobio-1")
                .setType(Type.HOST_DELETE)
                .setXmlBytes(ToolsTestData.loadBytes("host_delete.xml").read())
                .build());
    persistResource(
        new HistoryEntry.Builder()
            .setClientId("blobio-1")
            .setType(Type.HOST_UPDATE)
            .setXmlBytes(ToolsTestData.loadBytes("host_update.xml").read())
            .build());
  }

  @Test
  public void testSuccess_passSummarize() throws Exception {
    Map<String, Object> response =
        action.handleJsonRequest(
            ImmutableMap.of("summarize", "true", "registrars", ImmutableList.of("blobio")));

    for (Entry<String, Object> registrar : response.entrySet()) {
      assertThat(registrar.getKey()).matches("blobio");
      assertThat(registrar.getValue().toString()).containsMatch("Reqs passed: 19/19");
      assertThat(registrar.getValue().toString()).containsMatch("Overall: PASS");
    }
  }

  @Test
  public void testSuccess_passNotSummarized() throws Exception {
    Map<String, Object> response =
        action.handleJsonRequest(
            ImmutableMap.of("summarize", "false", "registrars", ImmutableList.of("blobio")));

    for (Entry<String, Object> registrar : response.entrySet()) {
      assertThat(registrar.getKey()).matches("blobio");
      String expectedOteStatus =
          "domain application creates landrush: 1\n"
              + "domain application creates sunrise: 1\n"
              + "domain application deletes: 2\n"
              + "domain application updates: 2\n"
              + ".*"
              + "domain creates idn: 1\n"
              + "domain creates with claims notice: 1\n"
              + "domain creates with fee: 1\n"
              + "domain creates with sec dns: 1\n"
              + ".*"
              + "domain deletes: 2\n"
              + ".*"
              + "domain restores: 1\n"
              + "domain transfer approves: 1\n"
              + "domain transfer cancels: 1\n"
              + "domain transfer rejects: 1\n"
              + "domain transfer requests: 1\n"
              + ".*"
              + "domain updates with sec dns: 1\n"
              + ".*"
              + "host creates subordinate: 1\n"
              + "host deletes: 1\n"
              + "host updates: 1\n"
              + ".*"
              + "Requirements passed: 19/19\n"
              + "Overall OT&E status: PASS\n";
      Pattern expectedOteStatusPattern = Pattern.compile(expectedOteStatus, Pattern.DOTALL);
      assertThat(registrar.getValue().toString()).containsMatch(expectedOteStatusPattern);
    }
  }

  @Test
  public void testFailure_missingHostDelete() throws Exception {
    deleteResource(hostDeleteHistoryEntry);

    Map<String, Object> response =
        action.handleJsonRequest(
            ImmutableMap.of("summarize", "false", "registrars", ImmutableList.of("blobio")));

    for (Entry<String, Object> registrar : response.entrySet()) {
      assertThat(registrar.getKey()).matches("blobio");
      String oteStatus = registrar.getValue().toString();

      String expectedOteStatus =
          "domain application creates landrush: 1\n"
              + "domain application creates sunrise: 1\n"
              + "domain application deletes: 2\n"
              + "domain application updates: 2\n"
              + ".*"
              + "domain creates idn: 1\n"
              + "domain creates with claims notice: 1\n"
              + "domain creates with fee: 1\n"
              + "domain creates with sec dns: 1\n"
              + ".*"
              + "domain deletes: 2\n"
              + ".*"
              + "domain restores: 1\n"
              + "domain transfer approves: 1\n"
              + "domain transfer cancels: 1\n"
              + "domain transfer rejects: 1\n"
              + "domain transfer requests: 1\n"
              + ".*"
              + "domain updates with sec dns: 1\n"
              + ".*"
              + "host creates subordinate: 1\n"
              + "host deletes: 0\n"
              + "host updates: 1\n"
              + ".*"
              + "Requirements passed: 18/19\n"
              + "Overall OT&E status: FAIL\n";
      Pattern expectedOteStatusPattern = Pattern.compile(expectedOteStatus, Pattern.DOTALL);
      assertThat(oteStatus).containsMatch(expectedOteStatusPattern);
    }
  }
}
