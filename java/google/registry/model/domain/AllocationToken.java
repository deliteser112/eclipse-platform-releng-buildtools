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

package google.registry.model.domain;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import google.registry.model.BackupGroupRoot;
import google.registry.model.Buildable;
import google.registry.model.annotations.ReportedOn;
import google.registry.model.reporting.HistoryEntry;

/** An entity representing an allocation token. */
@ReportedOn
@Entity
public class AllocationToken extends BackupGroupRoot implements Buildable {

  /** The allocation token string. */
  @Id String token;

  /** The key of the history entry for which the token was used. Null if not yet used. */
  Key<HistoryEntry> redemptionHistoryEntry;

  public String getToken() {
    return token;
  }

  public Key<HistoryEntry> getRedemptionHistoryEntry() {
    return redemptionHistoryEntry;
  }

  public boolean isRedeemed() {
    return redemptionHistoryEntry != null;
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link AllocationToken} objects, since they are immutable. */
  public static class Builder extends Buildable.Builder<AllocationToken> {
    public Builder() {}

    private Builder(AllocationToken instance) {
      super(instance);
    }

    public Builder setToken(String token) {
      checkArgumentNotNull(token, "token must not be null");
      checkArgument(!token.isEmpty(), "token must not be blank");
      getInstance().token = token;
      return this;
    }

    public Builder setRedemptionHistoryEntry(Key<HistoryEntry> redemptionHistoryEntry) {
      getInstance().redemptionHistoryEntry =
          checkArgumentNotNull(redemptionHistoryEntry, "redemptionHistoryEntry must not be null");
      return this;
    }
  }
}
