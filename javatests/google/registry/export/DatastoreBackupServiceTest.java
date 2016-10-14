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
import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.modules.ModulesService;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import java.util.Date;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link DatastoreBackupService}. */
@RunWith(MockitoJUnitRunner.class)
public class DatastoreBackupServiceTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Mock
  private ModulesService modulesService;

  private static final DateTime START_TIME = DateTime.parse("2014-08-01T01:02:03Z");

  private final DatastoreBackupService backupService = DatastoreBackupService.get();

  @Before
  public void before() throws Exception {
    inject.setStaticField(DatastoreBackupService.class, "modulesService", modulesService);
    when(modulesService.getVersionHostname("default", "ah-builtin-python-bundle"))
        .thenReturn("ah-builtin-python-bundle.default.localhost");

    persistBackupEntityWithName("backupA1");
    persistBackupEntityWithName("backupA2");
    persistBackupEntityWithName("backupA3");
    persistBackupEntityWithName("backupB1");
    persistBackupEntityWithName("backupB42");
  }

  private static void persistBackupEntityWithName(String name) {
    Entity entity = new Entity(DatastoreBackupService.BACKUP_INFO_KIND);
    entity.setProperty("name", name);
    entity.setProperty("kinds", ImmutableList.of("one", "two", "three"));
    entity.setProperty("start_time", new Date(START_TIME.getMillis()));
    getDatastoreService().put(entity);
  }

  @Test
  public void testSuccess_launchBackup() throws Exception {
    backupService.launchNewBackup(
        "export-snapshot", "backup1", "somebucket", ImmutableSet.of("foo", "bar"));
    assertTasksEnqueued("export-snapshot",
        new TaskMatcher()
            .url("/_ah/datastore_admin/backup.create")
            .header("Host", "ah-builtin-python-bundle.default.localhost")
            .method("GET")
            .param("name", "backup1_")
            .param("filesystem", "gs")
            .param("gs_bucket_name", "somebucket")
            .param("queue", "export-snapshot")
            .param("kind", "foo")
            .param("kind", "bar"));
  }

  private static final Function<DatastoreBackupInfo, String> BACKUP_NAME_GETTER =
      new Function<DatastoreBackupInfo, String>() {
        @Override
        public String apply(DatastoreBackupInfo backup) {
          return backup.getName();
        }};

  @Test
  public void testSuccess_findAllByNamePrefix() throws Exception {
    assertThat(transform(backupService.findAllByNamePrefix("backupA"), BACKUP_NAME_GETTER))
        .containsExactly("backupA1", "backupA2", "backupA3");
    assertThat(transform(backupService.findAllByNamePrefix("backupB"), BACKUP_NAME_GETTER))
        .containsExactly("backupB1", "backupB42");
    assertThat(transform(backupService.findAllByNamePrefix("backupB4"), BACKUP_NAME_GETTER))
        .containsExactly("backupB42");
    assertThat(backupService.findAllByNamePrefix("backupX")).isEmpty();
  }

  @Test
  public void testSuccess_findByName() throws Exception {
    assertThat(BACKUP_NAME_GETTER.apply(backupService.findByName("backupA1")))
        .isEqualTo("backupA1");
    assertThat(BACKUP_NAME_GETTER.apply(backupService.findByName("backupB4")))
        .isEqualTo("backupB42");
  }

  @Test
  public void testFailure_findByName_multipleMatchingBackups() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    backupService.findByName("backupA");
  }

  @Test
  public void testFailure_findByName_noMatchingBackups() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    backupService.findByName("backupX");
  }
}
