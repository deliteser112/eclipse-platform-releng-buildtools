// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.model.export;

import static google.registry.util.CollectionUtils.nullToEmptyImmutableCopy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.annotation.Entity;
import google.registry.model.Buildable;
import google.registry.model.common.CrossTldSingleton;
import google.registry.model.common.PersistedRangeLong;
import java.util.List;
import java.util.Set;

/** A saved cursor of exported log records so far. */
@Entity
public class LogsExportCursor extends CrossTldSingleton implements Buildable {

  /**
   * A set of timestamp ranges (expressesd in microseconds since the epoch) that have been exported
   * to GCS.
   */
  Set<PersistedRangeLong> exportedRanges;

  /**
   * A list of filenames that are pending import into bigquery. Files may not have been imported
   * into bigquery on previous iterations if there are gaps in the exported ranges.
   */
  List<String> filesPendingImport;

  public ImmutableSet<PersistedRangeLong> getExportedRanges() {
    return nullToEmptyImmutableCopy(exportedRanges);
  }

  public ImmutableList<String> getFilesPendingImport() {
    return nullToEmptyImmutableCopy(filesPendingImport);
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link LogsExportCursor} objects, since they are immutable. */
  public static class Builder extends Buildable.Builder<LogsExportCursor> {
    public Builder() {}

    private Builder(LogsExportCursor instance) {
      super(instance);
    }

    public Builder setExportedRanges(ImmutableSet<PersistedRangeLong> exportedRanges) {
      getInstance().exportedRanges = exportedRanges;
      return this;
    }

    public Builder setFilesPendingImport(ImmutableList<String> filesPendingImport) {
      getInstance().filesPendingImport = filesPendingImport;
      return this;
    }

    @Override
    public LogsExportCursor build() {
      return super.build();
    }
  }
}
