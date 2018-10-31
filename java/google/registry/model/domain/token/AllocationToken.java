// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.model.domain.token;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import google.registry.model.BackupGroupRoot;
import google.registry.model.Buildable;
import google.registry.model.CreateAutoTimestamp;
import google.registry.model.annotations.ReportedOn;
import google.registry.model.reporting.HistoryEntry;
import java.util.Optional;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/** An entity representing an allocation token. */
@ReportedOn
@Entity
public class AllocationToken extends BackupGroupRoot implements Buildable {

  /** The allocation token string. */
  @Id String token;

  /** The key of the history entry for which the token was used. Null if not yet used. */
  @Nullable @Index Key<HistoryEntry> redemptionHistoryEntry;

  /** The fully-qualified domain name that this token is limited to, if any. */
  @Nullable @Index String domainName;

  /** When this token was created. */
  CreateAutoTimestamp creationTime = CreateAutoTimestamp.create(null);

  public String getToken() {
    return token;
  }

  public Key<HistoryEntry> getRedemptionHistoryEntry() {
    return redemptionHistoryEntry;
  }

  public boolean isRedeemed() {
    return redemptionHistoryEntry != null;
  }

  public Optional<String> getDomainName() {
    return Optional.ofNullable(domainName);
  }

  public Optional<DateTime> getCreationTime() {
    return Optional.ofNullable(creationTime.getTimestamp());
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
      checkState(getInstance().token == null, "token can only be set once");
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

    public Builder setDomainName(@Nullable String domainName) {
      getInstance().domainName = domainName;
      return this;
    }

    @VisibleForTesting
    public Builder setCreationTimeForTest(DateTime creationTime) {
      checkState(
          getInstance().creationTime.getTimestamp() == null, "creationTime can only be set once");
      getInstance().creationTime = CreateAutoTimestamp.create(creationTime);
      return this;
    }
  }
}
