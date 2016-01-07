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

package google.registry.export;

import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Text;
import com.google.common.collect.ImmutableList;
import google.registry.export.DatastoreBackupInfo.BackupStatus;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DatastoreBackupInfo}. */
@RunWith(JUnit4.class)
public class DatastoreBackupInfoTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  private FakeClock clock = new FakeClock();

  private DateTime startTime = DateTime.parse("2014-08-01T01:02:03Z");

  private Entity backupEntity;  // Can't initialize until AppEngineRule has set up datastore.

  @Before
  public void before() throws Exception {
    inject.setStaticField(DatastoreBackupInfo.class, "clock", clock);
    backupEntity = new Entity("_unused_");
    backupEntity.setProperty("name", "backup1");
    backupEntity.setProperty("kinds", ImmutableList.of("one", "two", "three"));
    backupEntity.setProperty("start_time", new Date(startTime.getMillis()));
  }

  /** Force a roundtrip to datastore to ensure that we're simulating a freshly fetched entity. */
  private static Entity persistEntity(Entity entity) throws EntityNotFoundException {
    return getDatastoreService().get(getDatastoreService().put(entity));
  }

  @Test
  public void testSuccess_pendingBackup() throws Exception {
    DatastoreBackupInfo backup = new DatastoreBackupInfo(persistEntity(backupEntity));

    assertThat(backup.getName()).isEqualTo("backup1");
    assertThat(backup.getKinds()).containsExactly("one", "two", "three");
    assertThat(backup.getStartTime()).isEqualTo(startTime);
    assertThat(backup.getCompleteTime()).isAbsent();
    assertThat(backup.getGcsFilename()).isAbsent();
    assertThat(backup.getStatus()).isEqualTo(BackupStatus.PENDING);

    clock.setTo(startTime.plusMinutes(1));
    assertThat(backup.getRunningTime()).isEqualTo(Duration.standardMinutes(1));
    clock.setTo(startTime.plusMinutes(1).plusSeconds(42));
    assertThat(backup.getRunningTime()).isEqualTo(Duration.standardSeconds(102));
  }

  @Test
  public void testSuccess_completeBackup() throws Exception {
    DateTime completeTime = startTime.plusMinutes(1).plusSeconds(42);
    backupEntity.setProperty("complete_time", new Date(completeTime.getMillis()));
    backupEntity.setProperty("gs_handle", new Text("/gs/somebucket/timestamp.backup_info"));
    DatastoreBackupInfo backup = new DatastoreBackupInfo(persistEntity(backupEntity));

    assertThat(backup.getName()).isEqualTo("backup1");
    assertThat(backup.getKinds()).containsExactly("one", "two", "three");
    assertThat(backup.getStartTime()).isEqualTo(startTime);
    assertThat(backup.getCompleteTime().get()).isEqualTo(completeTime);
    assertThat(backup.getGcsFilename()).hasValue("gs://somebucket/timestamp.backup_info");
    assertThat(backup.getStatus()).isEqualTo(BackupStatus.COMPLETE);
    assertThat(backup.getRunningTime()).isEqualTo(Duration.standardSeconds(102));
  }

  @Test
  public void testFailure_missingName() throws Exception {
    backupEntity.removeProperty("name");
    thrown.expect(NullPointerException.class);
    new DatastoreBackupInfo(persistEntity(backupEntity));
  }

  @Test
  public void testFailure_missingKinds() throws Exception {
    backupEntity.removeProperty("kinds");
    thrown.expect(NullPointerException.class);
    new DatastoreBackupInfo(persistEntity(backupEntity));
  }

  @Test
  public void testFailure_missingStartTime() throws Exception {
    backupEntity.removeProperty("start_time");
    thrown.expect(NullPointerException.class);
    new DatastoreBackupInfo(persistEntity(backupEntity));
  }

  @Test
  public void testFailure_badGcsFilenameFormat() throws Exception {
    backupEntity.setProperty("gs_handle", new Text("foo"));
    thrown.expect(IllegalArgumentException.class);
    new DatastoreBackupInfo(persistEntity(backupEntity));
  }
}
