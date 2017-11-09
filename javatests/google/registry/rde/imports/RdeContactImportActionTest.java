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

package google.registry.rde.imports;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.getHistoryEntries;
import static google.registry.testing.DatastoreHelper.newContactResource;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResource;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.googlecode.objectify.Key;
import google.registry.config.RegistryConfig.ConfigModule;
import google.registry.gcs.GcsUtils;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.contact.ContactResource;
import google.registry.model.eppcommon.Trid;
import google.registry.model.reporting.HistoryEntry;
import google.registry.request.Response;
import google.registry.testing.FakeResponse;
import google.registry.testing.mapreduce.MapreduceTestCase;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdeContactImportAction}. */
@RunWith(JUnit4.class)
public class RdeContactImportActionTest extends MapreduceTestCase<RdeContactImportAction> {

  private static final ByteSource DEPOSIT_1_CONTACT =
      RdeImportsTestData.get("deposit_1_contact.xml");
  private static final String IMPORT_BUCKET_NAME = "import-bucket";
  private static final String IMPORT_FILE_NAME = "escrow-file.xml";

  private static final GcsService GCS_SERVICE =
      GcsServiceFactory.createGcsService(RetryParams.getDefaultInstance());

  private MapreduceRunner mrRunner;

  private Response response;

  @Before
  public void before() throws Exception {
    response = new FakeResponse();
    mrRunner = makeDefaultRunner();
    action = new RdeContactImportAction(
        mrRunner,
        response,
        IMPORT_BUCKET_NAME,
        IMPORT_FILE_NAME,
        Optional.of(3));
  }

  @Test
  public void test_mapreduceSuccessfullyImportsContact() throws Exception {
    pushToGcs(DEPOSIT_1_CONTACT);
    runMapreduce();
    List<ContactResource> contacts = ofy().load().type(ContactResource.class).list();
    assertThat(contacts).hasSize(1);
    checkContact(contacts.get(0));
  }

  @Test
  public void test_mapreduceSuccessfullyCreatesHistoryEntry() throws Exception {
    pushToGcs(DEPOSIT_1_CONTACT);
    runMapreduce();
    List<ContactResource> contacts = ofy().load().type(ContactResource.class).list();
    ContactResource contact = contacts.get(0);
    // verify history entry
    List<HistoryEntry> historyEntries = getHistoryEntries(contact);
    assertThat(historyEntries).hasSize(1);
    checkHistoryEntry(historyEntries.get(0), contact);
  }

  /** Ensures that a second pass on a contact does not import a new contact. */
  @Test
  public void test_mapreduceTwiceDoesNotDuplicateResources() throws Exception {
    pushToGcs(DEPOSIT_1_CONTACT);
    // Create contact and history entry first
    ContactResource existingContact = persistResource(
        newContactResource("contact1")
            .asBuilder()
            .setRepoId("contact1-TEST")
            .build());
    persistSimpleResource(createHistoryEntry(
        existingContact.getRepoId(),
        existingContact.getCurrentSponsorClientId(),
        loadContactXml(DEPOSIT_1_CONTACT)));
    // Simulate running a second import and verify that the resources
    // aren't imported twice (only one host, and one history entry)
    runMapreduce();
    List<ContactResource> contacts = ofy().load().type(ContactResource.class).list();
    assertThat(contacts).hasSize(1);
    ContactResource contact = contacts.get(0);
    // verify history entry
    List<HistoryEntry> historyEntries = getHistoryEntries(contact);
    assertThat(historyEntries).hasSize(1);
    checkHistoryEntry(historyEntries.get(0), contact);
  }

  private static HistoryEntry createHistoryEntry(String roid, String clid, byte[] objectXml) {
      return new HistoryEntry.Builder()
          .setType(HistoryEntry.Type.RDE_IMPORT)
          .setClientId(clid)
          .setTrid(Trid.create("client-trid", "server-trid"))
          .setModificationTime(DateTime.now(UTC))
          .setXmlBytes(objectXml)
          .setBySuperuser(true)
          .setReason("RDE Import")
          .setRequestedByRegistrar(false)
          .setParent(Key.create(null, ContactResource.class, roid))
          .build();
  }

  /** Verify history entry fields are correct */
  private void checkHistoryEntry(HistoryEntry entry, ContactResource parent) {
    assertThat(entry.getType()).isEqualTo(HistoryEntry.Type.RDE_IMPORT);
    assertThat(entry.getClientId()).isEqualTo(parent.getCurrentSponsorClientId());
    assertThat(entry.getXmlBytes().length).isGreaterThan(0);
    assertThat(entry.getBySuperuser()).isTrue();
    assertThat(entry.getReason()).isEqualTo("RDE Import");
    assertThat(entry.getRequestedByRegistrar()).isEqualTo(false);
    assertThat(entry.getParent()).isEqualTo(Key.create(parent));
  }

  /** Verifies that contact id and ROID match expected values */
  private void checkContact(ContactResource contact) {
    assertThat(contact.getContactId()).isEqualTo("contact1");
    assertThat(contact.getRepoId()).isEqualTo("contact1-TEST");
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  private void pushToGcs(ByteSource source) throws IOException {
    try (OutputStream outStream =
            new GcsUtils(GCS_SERVICE, ConfigModule.provideGcsBufferSize())
                .openOutputStream(new GcsFilename(IMPORT_BUCKET_NAME, IMPORT_FILE_NAME));
        InputStream inStream = source.openStream()) {
      ByteStreams.copy(inStream, outStream);
    }
  }

  private static byte[] loadContactXml(ByteSource source) throws IOException {
    byte[] result = new byte[((int) source.size())];
    try (InputStream inStream = source.openStream()) {
      ByteStreams.readFully(inStream, result);
    }
    return result;
  }
}
