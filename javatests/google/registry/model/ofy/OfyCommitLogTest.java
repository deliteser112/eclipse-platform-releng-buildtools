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

package google.registry.model.ofy;

import static com.google.appengine.api.datastore.EntityTranslator.convertToPb;
import static com.google.common.truth.Truth.assertThat;
import static com.googlecode.objectify.ObjectifyService.register;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.CommitLogBucket.getBucketKey;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;
import google.registry.model.BackupGroupRoot;
import google.registry.model.ImmutableObject;
import google.registry.model.common.EntityGroupRoot;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests ensuring {@link Ofy} saves transactions to {@link CommitLogManifest}. */
@RunWith(JUnit4.class)
public class OfyCommitLogTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));

  @Before
  public void before() throws Exception {
    register(Root.class);
    register(Child.class);
    inject.setStaticField(Ofy.class, "clock", clock);
  }

  @Test
  public void testTransact_doesNothing_noCommitLogIsSaved() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {}
    });
    assertThat(ofy().load().type(CommitLogManifest.class)).isEmpty();
  }

  @Test
  public void testTransact_savesDataAndCommitLog() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey())).now();
      }});
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now().value)
        .isEqualTo("value");
    assertThat(ofy().load().type(CommitLogManifest.class)).hasSize(1);
    assertThat(ofy().load().type(CommitLogMutation.class)).hasSize(1);
  }

  @Test
  public void testTransact_saveWithoutBackup_noCommitLogIsSaved() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().saveWithoutBackup().entity(Root.create(1, getCrossTldKey())).now();
      }});
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now().value)
        .isEqualTo("value");
    assertThat(ofy().load().type(CommitLogManifest.class)).isEmpty();
    assertThat(ofy().load().type(CommitLogMutation.class)).isEmpty();
  }

  @Test
  public void testTransact_deleteWithoutBackup_noCommitLogIsSaved() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().saveWithoutBackup().entity(Root.create(1, getCrossTldKey())).now();
      }});
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().deleteWithoutBackup().entity(Key.create(Root.class, 1));
      }});
    assertThat(ofy().load().key(Key.create(Root.class, 1)).now()).isNull();
    assertThat(ofy().load().type(CommitLogManifest.class)).isEmpty();
    assertThat(ofy().load().type(CommitLogMutation.class)).isEmpty();
  }

  @Test
  public void testTransact_savesEntity_itsProtobufFormIsStoredInCommitLog() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey())).now();
      }});
    final byte[] entityProtoBytes =
        ofy().load().type(CommitLogMutation.class).first().now().entityProtoBytes;
    // This transaction is needed so that save().toEntity() can access ofy().getTransactionTime()
    // when it attempts to set the update timestamp.
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        assertThat(entityProtoBytes).isEqualTo(
            convertToPb(ofy().save().toEntity(Root.create(1, getCrossTldKey()))).toByteArray());
      }});
  }

  @Test
  public void testTransact_savesEntity_mutationIsChildOfManifest() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey())).now();
      }});
    assertThat(ofy().load()
        .type(CommitLogMutation.class)
        .ancestor(ofy().load().type(CommitLogManifest.class).first().now()))
            .hasSize(1);
  }

  @Test
  public void testTransactNew_savesDataAndCommitLog() throws Exception {
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey())).now();
      }});
    assertThat(ofy().load()
        .key(Key.create(getCrossTldKey(), Root.class, 1))
        .now().value).isEqualTo("value");
    assertThat(ofy().load().type(CommitLogManifest.class)).hasSize(1);
    assertThat(ofy().load().type(CommitLogMutation.class)).hasSize(1);
  }

  @Test
  public void testTransact_multipleSaves_logsMultipleMutations() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey())).now();
        ofy().save().entity(Root.create(2, getCrossTldKey())).now();
      }});
    assertThat(ofy().load().type(CommitLogManifest.class)).hasSize(1);
    assertThat(ofy().load().type(CommitLogMutation.class)).hasSize(2);
  }

  @Test
  public void testTransact_deletion_deletesAndLogsWithoutMutation() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().saveWithoutBackup().entity(Root.create(1, getCrossTldKey())).now();
      }});
    clock.advanceOneMilli();
    final Key<Root> otherTldKey = Key.create(getCrossTldKey(), Root.class, 1);
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().key(otherTldKey);
      }});
    assertThat(ofy().load().key(otherTldKey).now()).isNull();
    assertThat(ofy().load().type(CommitLogManifest.class)).hasSize(1);
    assertThat(ofy().load().type(CommitLogMutation.class)).isEmpty();
    assertThat(ofy().load().type(CommitLogManifest.class).first().now().getDeletions())
        .containsExactly(otherTldKey);
  }

  @Test
  public void testTransactNew_deleteNotBackedUpKind_throws() throws Exception {
    final CommitLogManifest backupsArentAllowedOnMe =
        CommitLogManifest.create(getBucketKey(1), clock.nowUtc(), ImmutableSet.<Key<?>>of());
    thrown.expect(IllegalArgumentException.class, "Can't save/delete a @NotBackedUp");
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().entity(backupsArentAllowedOnMe);
      }});
  }

  @Test
  public void testTransactNew_saveNotBackedUpKind_throws() throws Exception {
    final CommitLogManifest backupsArentAllowedOnMe =
        CommitLogManifest.create(getBucketKey(1), clock.nowUtc(), ImmutableSet.<Key<?>>of());
    ofy().saveWithoutBackup().entity(backupsArentAllowedOnMe).now();
    thrown.expect(IllegalArgumentException.class, "Can't save/delete a @NotBackedUp");
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(backupsArentAllowedOnMe);
      }});
  }

  @Test
  public void testTransact_twoSavesOnSameKey_throws() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Multiple entries with same key");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
        ofy().save().entity(Root.create(1, getCrossTldKey()));
      }});
  }

  @Test
  public void testTransact_saveAndDeleteSameKey_throws() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Multiple entries with same key");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
        ofy().delete().entity(Root.create(1, getCrossTldKey()));
      }});
  }

  @Test
  public void testSavingRootAndChild_updatesTimestampOnBackupGroupRoot() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
    clock.advanceOneMilli();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
        ofy().save().entity(new Child());
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
  }

  @Test
  public void testSavingOnlyChild_updatesTimestampOnBackupGroupRoot() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
    clock.advanceOneMilli();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(new Child());
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
  }

  @Test
  public void testDeletingChild_updatesTimestampOnBackupGroupRoot() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
    clock.advanceOneMilli();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        // The fact that the child was never persisted is irrelevant.
        ofy().delete().entity(new Child());
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
  }

  @Test
  public void testReadingRoot_doesntUpdateTimestamp() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
    clock.advanceOneMilli();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        // Don't remove this line, as without saving *something* the commit log co/de will never
        // be invoked and the test will trivially pass
        ofy().save().entity(Root.create(2, getCrossTldKey()));
        ofy().load().entity(Root.create(1, getCrossTldKey()));
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc().minusMillis(1));
  }

  @Test
  public void testReadingChild_doesntUpdateTimestampOnBackupGroupRoot() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
    clock.advanceOneMilli();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        // Don't remove this line, as without saving *something* the commit log co/de will never
        // be invoked and the test will trivially pass
        ofy().save().entity(Root.create(2, getCrossTldKey()));
        ofy().load().entity(new Child());  // All Child objects are under Root(1).
      }});
    ofy().clearSessionCache();
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc().minusMillis(1));
  }

  @Test
  public void testSavingAcrossBackupGroupRoots_updatesCorrectTimestamps() throws Exception {
    // Create three roots.
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Root.create(1, getCrossTldKey()));
        ofy().save().entity(Root.create(2, getCrossTldKey()));
        ofy().save().entity(Root.create(3, getCrossTldKey()));
      }});
    ofy().clearSessionCache();
    for (int i = 1; i <= 3; i++) {
      assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, i)).now()
          .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
    }
    clock.advanceOneMilli();
    // Mutate one root, and a child of a second, ignoring the third.
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(new Child());  // All Child objects are under Root(1).
        ofy().save().entity(Root.create(2, getCrossTldKey()));
      }});
    ofy().clearSessionCache();
    // Child was touched.
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 1)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
    // Directly touched.
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 2)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc());
    // Wasn't touched.
    assertThat(ofy().load().key(Key.create(getCrossTldKey(), Root.class, 3)).now()
        .getUpdateAutoTimestamp().getTimestamp()).isEqualTo(clock.nowUtc().minusMillis(1));
  }

  @Entity
  static class Root extends BackupGroupRoot {

    @Parent
    Key<EntityGroupRoot> parent;

    @Id
    long id;

    String value;

    static Root create(long id, Key<EntityGroupRoot> parent) {
      Root result = new Root();
      result.parent = parent;
      result.id = id;
      result.value = "value";
      return result;
    }
  }

  @Entity
  static class Child extends ImmutableObject {
    @Parent
    Key<Root> parent = Key.create(Root.create(1, getCrossTldKey()));

    @Id
    long id = 1;
  }
}
