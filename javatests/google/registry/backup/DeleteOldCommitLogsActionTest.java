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

package google.registry.backup;

import static com.google.common.truth.Truth.assertThat;
import static org.joda.time.Duration.millis;

import com.googlecode.objectify.VoidWork;
import google.registry.config.TestRegistryConfig;
import google.registry.model.ofy.CommitLogManifest;
import google.registry.model.ofy.CommitLogMutation;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.RegistryConfigRule;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeleteOldCommitLogsAction}. */
@RunWith(JUnit4.class)
public class DeleteOldCommitLogsActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final RegistryConfigRule configRule = new RegistryConfigRule();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  private final Ofy ofy = new Ofy(clock);
  private final DeleteOldCommitLogsAction task = new DeleteOldCommitLogsAction();

  @Before
  public void before() throws Exception {
    task.bucketNum = 1;
    task.clock = clock;
    task.maxAge = Duration.millis(2);
    task.maxDeletes = 4;
    task.ofy = ofy;
  }

  @Test
  public void testRun_noCommitLogs_doesNothing() throws Exception {
    assertManifestAndMutationCounts(0, 0);
    task.run();
    assertManifestAndMutationCounts(0, 0);
  }

  @Test
  public void testRun_commitLogNewerThanThreshold_doesntGetDeleted() throws Exception {
    createCommitLog();
    clock.advanceOneMilli();
    assertManifestAndMutationCounts(1, 2);
    task.run();
    assertManifestAndMutationCounts(1, 2);
  }

  @Test
  public void testRun_commitLogEqualToThreshold_doesntGetDeleted() throws Exception {
    createCommitLog();
    clock.advanceBy(millis(2));
    task.run();
    assertManifestAndMutationCounts(1, 2);
  }

  @Test
  public void testRun_commitLogOlderThanThreshold_getsDeleted() throws Exception {
    createCommitLog();
    clock.advanceBy(millis(3));
    task.run();
    assertManifestAndMutationCounts(0, 0);
  }

  @Test
  public void testRun_oneOlderThanThresholdAndOneNewer_onlyOldOneIsDeleted() throws Exception {
    createCommitLog();
    clock.advanceBy(millis(3));
    createCommitLog();
    assertManifestAndMutationCounts(2, 4);
    task.run();
    assertManifestAndMutationCounts(1, 2);
  }

  @Test
  public void testRun_twoOlderThanThreshold_bothGetDeletedInSameTransaction() throws Exception {
    task.maxDeletes = 2;
    createCommitLog();
    clock.advanceOneMilli();
    createCommitLog();
    clock.advanceBy(millis(3));
    assertManifestAndMutationCounts(2, 4);
    task.run();
    assertManifestAndMutationCounts(0, 0);
  }

  @Test
  public void testRun_twoOlderThanThreshold_bothGetDeletedInTwoTransactions() throws Exception {
    task.maxDeletes = 1;
    createCommitLog();
    clock.advanceOneMilli();
    createCommitLog();
    clock.advanceBy(millis(3));
    createCommitLog();
    assertManifestAndMutationCounts(3, 6);
    task.run();
    assertManifestAndMutationCounts(2, 4);
    task.run();
    assertManifestAndMutationCounts(1, 2);
  }

  @Test
  public void testRun_commitLogOlderButInADifferentBucket_doesntGetDeleted() throws Exception {
    createCommitLog();
    clock.advanceBy(millis(31337));
    configRule.override(new TestRegistryConfig() {
      @Override public int getCommitLogBucketCount() { return 2; }
    });
    task.bucketNum = 2;
    task.run();
    assertManifestAndMutationCounts(1, 2);
  }

  @Test
  public void testRun_lessThanATenthOfOldData_doesntGetDeleted() throws Exception {
    task.maxDeletes = 20;
    createCommitLog();
    clock.advanceBy(millis(2));
    task.run();
    assertManifestAndMutationCounts(1, 2);
  }

  private void assertManifestAndMutationCounts(int manifestCount, int mutationCount) {
    assertThat(ofy.load().type(CommitLogManifest.class).count()).isEqualTo(manifestCount);
    assertThat(ofy.load().type(CommitLogMutation.class).count()).isEqualTo(mutationCount);
  }

  private void createCommitLog() {
    ofy.transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy.save().entity(
            Registrar.loadByClientId("NewRegistrar").asBuilder()
                .setEmailAddress("pumpkin@cat.test")
                .build());
        ofy.save().entity(
            Registrar.loadByClientId("TheRegistrar").asBuilder()
                .setReferralUrl("http://justine.test")
                .build());
      }});
  }
}
