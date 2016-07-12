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

package google.registry.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.isActive;
import static google.registry.testing.DatastoreHelper.getHistoryEntriesOfType;
import static google.registry.testing.HistoryEntrySubject.assertAboutHistoryEntries;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import google.registry.model.EppResource;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferStatus;
import google.registry.testing.TruthChainer.And;
import google.registry.testing.TruthChainer.Which;
import java.util.List;
import java.util.Objects;
import org.joda.time.DateTime;

/** Base Truth subject for asserting things about epp resources. */
abstract class AbstractEppResourceSubject
    <T extends EppResource, S extends AbstractEppResourceSubject<T, S>> extends Subject<S, T> {

  public AbstractEppResourceSubject(FailureStrategy strategy, T subject) {
    super(strategy, checkNotNull(subject));
  }

  private List<HistoryEntry> getHistoryEntries() {
    return DatastoreHelper.getHistoryEntries(getSubject());
  }

  @SuppressWarnings("unchecked")
  protected And<S> andChainer() {
    return new And<S>((S) this);
  }

  @Override
  public String getDisplaySubject() {
    return String.format(
        "%s with foreign key '%s'",
        getSubject().getClass().getSimpleName(),
        getSubject().getForeignKey());
  }

  public And<S> hasRepoId(long roid) {
    return hasValue(roid, getSubject().getRepoId(), "has repoId");
  }

  public And<S> hasNoHistoryEntries() {
    if (!getHistoryEntries().isEmpty()) {
      fail("has no history entries");
    }
    return andChainer();
  }

  public And<S> hasNumHistoryEntries(int num) {
    if (getHistoryEntries().size() != num) {
      failWithBadResults("has this number of history entries", num, getHistoryEntries().size());
    }
    return andChainer();
  }

  public And<S> hasNumHistoryEntriesOfType(HistoryEntry.Type type, int num) {
    List<HistoryEntry> entries = getHistoryEntriesOfType(getSubject(), type);
    if (entries.size() != num) {
      failWithBadResults(
          String.format("has this number of history entries of type %s", type.toString()),
          num,
          entries.size());
    }
    return andChainer();
  }

  public And<S> hasOneHistoryEntryEachOfTypes(HistoryEntry.Type ... types) {
    hasNumHistoryEntries(types.length);
    for (HistoryEntry.Type type : types) {
      hasNumHistoryEntriesOfType(type, 1);
    }
    return andChainer();
  }

  public And<S> hasOnlyOneHistoryEntry() {
    int numHistoryEntries = getHistoryEntries().size();
    if (numHistoryEntries != 1) {
      fail(String.format("has exactly one history entry (it has %d)", numHistoryEntries));
    }
    return andChainer();
  }

  public HistoryEntrySubject hasOnlyOneHistoryEntryWhich() {
    hasOnlyOneHistoryEntry();
    return assertAboutHistoryEntries().that(getHistoryEntries().get(0)).withCustomDisplaySubject(
        "the only history entry for " + getDisplaySubject());
  }

  public Which<HistoryEntrySubject> hasHistoryEntryAtIndex(int index) {
    List<HistoryEntry> historyEntries = getHistoryEntries();
    if (historyEntries.size() < index + 1) {
      failWithBadResults(
          "has at least number of history entries", index + 1, historyEntries.size());
    }
    return new Which<HistoryEntrySubject>(assertAboutHistoryEntries()
        .that(getHistoryEntries().get(index)).withCustomDisplaySubject(String.format(
            "the history entry for %s at index %s", getDisplaySubject(), index)));
  }

  public And<S> hasStatusValue(StatusValue statusValue) {
    if (!getSubject().getStatusValues().contains(statusValue)) {
      failWithRawMessage("%s should have had status value %s", getDisplaySubject(), statusValue);
    }
    return andChainer();
  }

  public And<S> doesNotHaveStatusValue(StatusValue statusValue) {
    if (getSubject().getStatusValues().contains(statusValue)) {
      failWithRawMessage(
          "%s should not have had status value %s", getDisplaySubject(), statusValue);
    }
    return andChainer();
  }

  public And<S> hasExactlyStatusValues(StatusValue... statusValues) {
    if (!ImmutableSet.copyOf(getSubject().getStatusValues())
        .equals(ImmutableSet.copyOf(statusValues))) {
      assertThat(getSubject().getStatusValues()).named("status values for " + getDisplaySubject())
          .containsExactly((Object[]) statusValues);
    }
    return andChainer();
  }

  public And<S> hasDeletionTime(DateTime deletionTime) {
    return hasValue(
        deletionTime,
        getSubject().getDeletionTime(),
        "has deletionTime");
  }

  public And<S> hasLastTransferTime(DateTime lastTransferTime) {
    return hasValue(
        lastTransferTime,
        getSubject().getLastTransferTime(),
        "has lastTransferTime");
  }

  public And<S> hasLastTransferTimeNotEqualTo(DateTime lastTransferTime) {
    return doesNotHaveValue(
        lastTransferTime,
        getSubject().getLastTransferTime(),
        "lastTransferTime");
  }

  public And<S> hasLastEppUpdateTime(DateTime lastUpdateTime) {
    return hasValue(
        lastUpdateTime,
        getSubject().getLastEppUpdateTime(),
        "has lastEppUpdateTime");
  }

  public And<S> hasLastEppUpdateTimeAtLeast(DateTime before) {
    DateTime lastEppUpdateTime = getSubject().getLastEppUpdateTime();
    if (lastEppUpdateTime == null || before.isAfter(lastEppUpdateTime)) {
      failWithBadResults("has lastEppUpdateTime at least", before, lastEppUpdateTime);
    }
    return andChainer();
  }

  public And<S> hasLastEppUpdateClientId(String clientId) {
    return hasValue(
        clientId,
        getSubject().getLastEppUpdateClientId(),
        "has lastEppUpdateClientId");
  }

  public And<S> hasCurrentSponsorClientId(String clientId) {
    return hasValue(
        clientId,
        getSubject().getCurrentSponsorClientId(),
        "has currentSponsorClientId");
  }

  public And<S> hasTransferStatus(TransferStatus transferStatus) {
    return hasValue(
        transferStatus,
        getSubject().getTransferData().getTransferStatus(),
        "has transferStatus");
  }

  public And<S> hasTransferRequestClientTrid(String clTrid) {
    return hasValue(
        clTrid,
        getSubject().getTransferData().getTransferRequestTrid().getClientTransactionId(),
        "has trid");
  }

  public And<S> hasPendingTransferExpirationTime(DateTime pendingTransferExpirationTime) {
    return hasValue(
        pendingTransferExpirationTime,
        getSubject().getTransferData().getPendingTransferExpirationTime(),
        "has pendingTransferExpirationTime");
  }

  public And<S> hasTransferGainingClientId(String gainingClientId) {
    return hasValue(
        gainingClientId,
        getSubject().getTransferData().getGainingClientId(),
        "has transfer ga");
  }

  public And<S> hasTransferLosingClientId(String losingClientId) {
    return hasValue(
        losingClientId,
        getSubject().getTransferData().getLosingClientId(),
        "has transfer losingClientId");
  }

  public And<S> isActiveAt(DateTime time) {
    if (!isActive(getSubject(), time)) {
      fail("is active at " + time);
    }
    return andChainer();
  }

  protected void failWithBadResults(String dualVerb, Object expected, Object actual) {
    failWithBadResults(dualVerb, expected, dualVerb, actual);
  }

  protected <E> And<S> hasValue(E expected, E actual, String verb) {
    if (!Objects.equals(expected, actual)) {
      failWithBadResults(verb, expected, actual);
    }
    return andChainer();
  }

  protected <E> And<S> doesNotHaveValue(E badValue, E actual, String valueName) {
    if (Objects.equals(badValue, actual)) {
      fail("has " + valueName + " not equal to " + badValue);
    }
    return andChainer();
  }
}
