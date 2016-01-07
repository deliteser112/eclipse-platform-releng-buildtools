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

import static com.google.common.base.Preconditions.checkState;
import static google.registry.backup.BackupUtils.GcsMetadataKeys.LOWER_BOUND_CHECKPOINT;
import static google.registry.backup.ExportCommitLogDiffAction.DIFF_FILE_PREFIX;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static google.registry.util.DateTimeUtils.isBeforeOrAt;
import static google.registry.util.DateTimeUtils.latestOf;

import com.google.appengine.tools.cloudstorage.GcsFileMetadata;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.ListItem;
import com.google.appengine.tools.cloudstorage.ListOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import google.registry.backup.BackupModule.Backups;
import google.registry.config.ConfigModule.Config;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** Utility class to list commit logs diff files stored on GCS. */
class GcsDiffFileLister {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject GcsService gcsService;
  @Inject @Config("commitLogGcsBucket") String gcsBucket;
  @Inject @Backups ListeningExecutorService executor;
  @Inject GcsDiffFileLister() {}

  List<GcsFileMetadata> listDiffFiles(DateTime fromTime) {
    logger.info("Requested restore from time: " + fromTime);
    // List all of the diff files on GCS and build a map from each file's upper checkpoint time
    // (extracted from the filename) to its asynchronously-loaded metadata, keeping only files with
    // an upper checkpoint time > fromTime.
    Map<DateTime, ListenableFuture<GcsFileMetadata>> upperBoundTimesToMetadata = new HashMap<>();
    Iterator<ListItem> listItems;
    try {
      // TODO(b/23554360): Use a smarter prefixing strategy to speed this up.
      listItems = gcsService.list(
          gcsBucket,
          new ListOptions.Builder().setPrefix(DIFF_FILE_PREFIX).build());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    DateTime lastUpperBoundTime = START_OF_TIME;
    while (listItems.hasNext()) {
      final String filename = listItems.next().getName();
      DateTime upperBoundTime = DateTime.parse(filename.substring(DIFF_FILE_PREFIX.length()));
      if (isBeforeOrAt(fromTime, upperBoundTime)) {
        upperBoundTimesToMetadata.put(upperBoundTime, executor.submit(
            new Callable<GcsFileMetadata>() {
              @Override
              public GcsFileMetadata call() throws Exception {
                return getMetadata(filename);
              }}));
      }
      lastUpperBoundTime = latestOf(upperBoundTime, lastUpperBoundTime);
    }
    if (upperBoundTimesToMetadata.isEmpty()) {
      logger.info("No files found");
      return ImmutableList.of();
    }
    // GCS file listing is eventually consistent, so it's possible that we are missing a file. The
    // metadata of a file is sufficient to identify the preceding file, so if we start from the
    // last file and work backwards we can verify that we have no holes in our chain (although we
    // may be missing files at the end).
    ImmutableList.Builder<GcsFileMetadata> filesBuilder = new ImmutableList.Builder<>();
    logger.info("Restoring until: " + lastUpperBoundTime);
    DateTime checkpointTime = lastUpperBoundTime;
    while (checkpointTime.isAfter(fromTime)) {
      GcsFileMetadata metadata;
      if (upperBoundTimesToMetadata.containsKey(checkpointTime)) {
        metadata = Futures.getUnchecked(upperBoundTimesToMetadata.get(checkpointTime));
      } else {
        String filename = DIFF_FILE_PREFIX + checkpointTime;
        logger.info("Patching GCS list; discovered file " + filename);
        metadata = getMetadata(filename);
        checkState(metadata != null, "Could not read metadata for file %s", filename);
      }
      filesBuilder.add(metadata);
      checkpointTime = getLowerBoundTime(metadata);
    }
    ImmutableList<GcsFileMetadata> files = filesBuilder.build().reverse();
    logger.info("Actual restore from time: " + getLowerBoundTime(files.get(0)));
    logger.infofmt("Found %d files to restore", files.size());
    return files;
  }

  private DateTime getLowerBoundTime(GcsFileMetadata metadata) {
    return DateTime.parse(metadata.getOptions().getUserMetadata().get(LOWER_BOUND_CHECKPOINT));
  }

  private GcsFileMetadata getMetadata(String filename) {
    try {
      return gcsService.getMetadata(new GcsFilename(gcsBucket, filename));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
