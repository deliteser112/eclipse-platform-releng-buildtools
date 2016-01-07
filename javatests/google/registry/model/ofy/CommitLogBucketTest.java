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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.CommitLogBucket.getBucketKey;
import static google.registry.model.ofy.CommitLogBucket.loadAllBuckets;
import static google.registry.model.ofy.CommitLogBucket.loadBucket;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.annotation.Cache;
import google.registry.config.TestRegistryConfig;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.InjectRule;
import google.registry.testing.RegistryConfigRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CommitLogBucket}. */
@RunWith(JUnit4.class)
public class CommitLogBucketTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final RegistryConfigRule configRule = new RegistryConfigRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  CommitLogBucket bucket;

  @Before
  public void before() {
    // Use 10 buckets to make the tests below more realistic.
    configRule.override(new TestRegistryConfig() {
      @Override
      public int getCommitLogBucketCount() {
        return 10;
      }});
    // Save the bucket with some non-default properties set so that we can distinguish a correct
    // load from one that returns a newly created bucket instance.
    bucket = persistResource(
        new CommitLogBucket.Builder()
            .setLastWrittenTime(END_OF_TIME)
            .setBucketNum(1)
            .build());
  }

  @Test
  public void test_getBucketKey_createsBucketKeyInDefaultNamespace() {
    // Key.getNamespace() returns the empty string for the default namespace, not null.
    assertThat(getBucketKey(1).getRaw().getNamespace()).isEmpty();
  }

  @Test
  public void test_getBucketKey_bucketNumberTooLow_throws() {
    thrown.expect(IllegalArgumentException.class, "0 not in [");
    getBucketKey(0);
  }

  @Test
  public void test_getBucketKey_bucketNumberTooHigh_throws() {
    thrown.expect(IllegalArgumentException.class, "11 not in [");
    getBucketKey(11);
  }

  @Test
  public void test_getArbitraryBucketId_withSupplierOverridden() {
    inject.setStaticField(
        CommitLogBucket.class, "bucketIdSupplier", Suppliers.ofInstance(4));  // xkcd.com/221
    // Try multiple times just in case it's actually still random.  If it is, the probability of
    // this test passing is googol^-1, so I think we're pretty safe.
    for (int i = 0; i < 100; i++) {
      assertThat(CommitLogBucket.getArbitraryBucketId()).isEqualTo(4);
    }
  }

  @Test
  public void test_loadBucket_loadsTheBucket() {
    assertThat(loadBucket(getBucketKey(1))).isEqualTo(bucket);
  }

  @Test
  public void test_loadBucket_forNonexistentBucket_returnsNewBucket() {
    assertThat(loadBucket(getBucketKey(10))).isEqualTo(
        new CommitLogBucket.Builder().setBucketNum(10).build());
  }

  @Test
  public void test_loadAllBuckets_loadsExistingBuckets_orNewOnesIfNonexistent() {
    ImmutableSet<CommitLogBucket> buckets = loadAllBuckets();
    assertThat(buckets).hasSize(10);
    assertThat(buckets).contains(bucket);
    for (int i = 2; i <= 10; ++i) {
      assertThat(buckets).contains(
          new CommitLogBucket.Builder().setBucketNum(i).build());
    }
  }

  @Test
  public void test_noCacheAnnotation() {
    // Don't ever put @Cache on CommitLogBucket; it could mess up the checkpointing algorithm.
    assertThat(CommitLogBucket.class.isAnnotationPresent(Cache.class)).isFalse();
  }
}
