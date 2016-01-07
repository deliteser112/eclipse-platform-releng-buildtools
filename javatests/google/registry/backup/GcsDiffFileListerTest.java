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

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static google.registry.backup.BackupUtils.GcsMetadataKeys.LOWER_BOUND_CHECKPOINT;
import static google.registry.backup.ExportCommitLogDiffAction.DIFF_FILE_PREFIX;
import static java.lang.reflect.Proxy.newProxyInstance;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.tools.cloudstorage.GcsFileMetadata;
import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.ListItem;
import com.google.appengine.tools.cloudstorage.ListResult;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.googlecode.objectify.ObjectifyService;
import google.registry.testing.AppEngineRule;
import google.registry.testing.TestObject;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GcsDiffFileLister}. */
@RunWith(JUnit4.class)
public class GcsDiffFileListerTest {

  static final String GCS_BUCKET = "gcs bucket";

  final DateTime now = DateTime.now(UTC);
  final GcsDiffFileLister diffLister = new GcsDiffFileLister();
  final GcsService gcsService = GcsServiceFactory.createGcsService();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Before
  public void before() throws Exception {
    ObjectifyService.register(TestObject.class);
    diffLister.gcsService = gcsService;
    diffLister.gcsBucket = GCS_BUCKET;
    diffLister.executor = newDirectExecutorService();
    for (int i = 0; i < 5; i++) {
      gcsService.createOrReplace(
          new GcsFilename(GCS_BUCKET, DIFF_FILE_PREFIX + now.minusMinutes(i)),
          new GcsFileOptions.Builder()
              .addUserMetadata(LOWER_BOUND_CHECKPOINT, now.minusMinutes(i + 1).toString())
              .build(),
          ByteBuffer.wrap(new byte[]{1, 2, 3}));
    }
  }

  private Iterable<DateTime> extractTimesFromDiffFiles(List<GcsFileMetadata> diffFiles) {
    return transform(
        diffFiles,
        new Function<GcsFileMetadata, DateTime>() {
          @Override
          public DateTime apply(GcsFileMetadata metadata) {
            return DateTime.parse(
                metadata.getFilename().getObjectName().substring(DIFF_FILE_PREFIX.length()));
          }});
  }

  @Test
  public void testList_noFilesFound() {
    DateTime fromTime = now.plusMillis(1);
    assertThat(extractTimesFromDiffFiles(diffLister.listDiffFiles(fromTime))).isEmpty();
  }

  @Test
  public void testList_findsFiles_backToFromTimeExclusive() {
    DateTime fromTime = now.minusMinutes(2);
    assertThat(extractTimesFromDiffFiles(diffLister.listDiffFiles(fromTime)))
        .containsExactly(now.minusMinutes(1), now)
        .inOrder();
  }

  @Test
  public void testList_patchesHoles() throws Exception {
    // Fake out the GCS list() method to return only the first and last file.
    // We can't use Mockito.spy() because GcsService's impl is final.
    diffLister.gcsService = (GcsService) newProxyInstance(
        GcsService.class.getClassLoader(),
        new Class<?>[] {GcsService.class},
        new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("list")) {
              // ListResult is an incredibly annoying thing to construct. It needs to be fed from a
              // Callable that returns Iterators, each representing a batch of results.
              return new ListResult(new Callable<Iterator<ListItem>>() {
                boolean called = false;

                @Override
                public Iterator<ListItem> call() throws Exception {
                  try {
                    return called ? null : Iterators.forArray(
                        new ListItem.Builder()
                            .setName(DIFF_FILE_PREFIX + now)
                            .build(),
                        new ListItem.Builder()
                            .setName(DIFF_FILE_PREFIX + now.minusMinutes(4))
                            .build());
                  } finally {
                    called = true;
                  }
                }});
            }
            return method.invoke(gcsService, args);
          }});
    DateTime fromTime = now.minusMinutes(4).minusSeconds(1);
    // Request all files with checkpoint > fromTime.
    assertThat(extractTimesFromDiffFiles(diffLister.listDiffFiles(fromTime)))
        .containsExactly(
            now.minusMinutes(4),
            now.minusMinutes(3),
            now.minusMinutes(2),
            now.minusMinutes(1),
            now)
        .inOrder();
  }
}
