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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Text;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import google.registry.util.Clock;
import google.registry.util.NonFinalForTesting;
import google.registry.util.SystemClock;
import java.util.Date;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Container for information about a datastore backup. */
public class DatastoreBackupInfo {

  @NonFinalForTesting
  private static Clock clock = new SystemClock();

  /** The possible status values for a datastore backup. */
  public enum BackupStatus { PENDING, COMPLETE }

  /** The name of the datastore backup. */
  private final String backupName;

  /** The entity kinds included in this datastore backup. */
  private final ImmutableSet<String> kinds;

  /** The start time of the datastore backup. */
  private final DateTime startTime;

  /** The completion time of the datastore backup, present if it has completed. */
  private final Optional<DateTime> completeTime;

  /**
   * The GCS filename to which the backup's top-level .backup_info manifest file has been written,
   * present if the backup has completed.
   */
  private final Optional<String> gcsFilename;

  /** DatastoreBackupInfo instances should only be obtained via DatastoreBackupService. */
  DatastoreBackupInfo(Entity backupEntity) {
    backupName = (String) checkNotNull(backupEntity.getProperty("name"), "name");
    @SuppressWarnings("unchecked")
    List<String> rawKinds = (List<String>) checkNotNull(backupEntity.getProperty("kinds"), "kinds");
    Date rawStartTime = (Date) checkNotNull(backupEntity.getProperty("start_time"), "start_time");
    Date rawCompleteTime = (Date) backupEntity.getProperty("complete_time");
    Text rawGcsFilename = (Text) backupEntity.getProperty("gs_handle");

    kinds = ImmutableSet.copyOf(rawKinds);
    startTime = new DateTime(rawStartTime).withZone(UTC);
    completeTime = Optional.fromNullable(
        rawCompleteTime == null ? null : new DateTime(rawCompleteTime).withZone(UTC));
    gcsFilename = Optional.fromNullable(
        rawGcsFilename == null ? null : gcsPathToUri(rawGcsFilename.getValue()));
  }

  /** This constructor is only exposed for test purposes. */
  @VisibleForTesting
  DatastoreBackupInfo(
      String backupName,
      DateTime startTime,
      Optional<DateTime> completeTime,
      ImmutableSet<String> kinds,
      Optional<String> gcsFilename) {
    this.backupName = backupName;
    this.startTime = startTime;
    this.completeTime = completeTime;
    this.kinds = kinds;
    this.gcsFilename = gcsFilename;
  }

  /**
   * Rewrite a GCS path as stored by Datastore Admin (with a "/gs/" prefix) to the more standard
   * URI format that uses a "gs://" scheme prefix.
   */
  private static String gcsPathToUri(String backupGcsPath) {
    checkArgument(backupGcsPath.startsWith("/gs/"), "GCS path not in expected format");
    return backupGcsPath.replaceFirst("/gs/", "gs://");
  }

  public String getName() {
    return backupName;
  }

  public ImmutableSet<String> getKinds() {
    return kinds;
  }

  public BackupStatus getStatus() {
    return completeTime.isPresent() ? BackupStatus.COMPLETE : BackupStatus.PENDING;
  }

  public DateTime getStartTime() {
    return startTime;
  }

  public Optional<DateTime> getCompleteTime() {
    return completeTime;
  }

  /**
   * Returns the length of time the backup ran for (if completed) or the length of time since the
   * backup started (if it has not completed).
   */
  public Duration getRunningTime() {
    return new Duration(startTime, completeTime.or(clock.nowUtc()));
  }

  public Optional<String> getGcsFilename() {
    return gcsFilename;
  }

  /** Returns a string version of key information about the backup. */
  public String getInformation() {
    return Joiner.on('\n')
        .join(
            "Backup name: " + backupName,
            "Status: " + getStatus(),
            "Started: " + startTime,
            "Ended: " + completeTime.orNull(),
            "Duration: " + Ascii.toLowerCase(getRunningTime().toPeriod().toString().substring(2)),
            "GCS: " + gcsFilename.orNull(),
            "Kinds: " + kinds,
            "");
  }
}
