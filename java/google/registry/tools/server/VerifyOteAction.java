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

package google.registry.tools.server;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Maps.toMap;
import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.isNullOrEmpty;
import static google.registry.util.DomainNameUtils.ACE_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import google.registry.flows.EppException;
import google.registry.model.domain.DomainCommand;
import google.registry.model.domain.fee.FeeCreateCommandExtension;
import google.registry.model.domain.launch.LaunchCreateExtension;
import google.registry.model.domain.secdns.SecDnsCreateExtension;
import google.registry.model.domain.secdns.SecDnsUpdateExtension;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.EppInput.ResourceCommandWrapper;
import google.registry.model.host.HostCommand;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.reporting.HistoryEntry.Type;
import google.registry.request.Action;
import google.registry.request.JsonActionRunner;
import google.registry.request.JsonActionRunner.JsonAction;
import google.registry.request.auth.Auth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * A servlet that verifies a registrar's OTE status. Note that this is eventually consistent, so
 * OT&amp;E commands that have been run just previously to verification may not be picked up yet.
 */
@Action(
  path = VerifyOteAction.PATH,
  method = Action.Method.POST,
  auth = Auth.AUTH_INTERNAL_OR_ADMIN
)
public class VerifyOteAction implements Runnable, JsonAction {

  public static final String PATH = "/_dr/admin/verifyOte";

  @Inject JsonActionRunner jsonActionRunner;

  @Inject
  VerifyOteAction() {}

  @Override
  public void run() {
    jsonActionRunner.run(this);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> handleJsonRequest(Map<String, ?> json) {
    final boolean summarize = Boolean.parseBoolean((String) json.get("summarize"));
    return toMap(
        (List<String>) json.get("registrars"), registrar -> checkRegistrar(registrar, summarize));
  }

  /** Checks whether the provided registrar has passed OT&amp;E and returns relevant information. */
  private String checkRegistrar(String registrarName, boolean summarize) {
    HistoryEntryStats historyEntryStats =
        new HistoryEntryStats().recordRegistrarHistory(registrarName);
    List<String> failureMessages = historyEntryStats.findFailures();
    int testsPassed = StatType.NUM_REQUIREMENTS - failureMessages.size();
    String status = failureMessages.isEmpty() ? "PASS" : "FAIL";
    return summarize
        ? String.format(
            "# actions: %4d - Reqs: [%s] %2d/%2d - Overall: %s",
            historyEntryStats.statCounts.size(),
            historyEntryStats.toSummary(),
            testsPassed,
            StatType.NUM_REQUIREMENTS,
            status)
        : String.format(
            "%s\n%s\nRequirements passed: %2d/%2d\nOverall OT&E status: %s\n",
            historyEntryStats,
            Joiner.on('\n').join(failureMessages),
            testsPassed,
            StatType.NUM_REQUIREMENTS,
            status);
  }

  private static final Predicate<EppInput> HAS_CLAIMS_NOTICE =
      eppInput -> {
        Optional<LaunchCreateExtension> launchCreate =
            eppInput.getSingleExtension(LaunchCreateExtension.class);
        return launchCreate.isPresent() && launchCreate.get().getNotice() != null;
      };

  private static final Predicate<EppInput> HAS_SEC_DNS =
      eppInput ->
          (eppInput.getSingleExtension(SecDnsCreateExtension.class).isPresent())
              || (eppInput.getSingleExtension(SecDnsUpdateExtension.class).isPresent());
  private static final Predicate<EppInput> IS_SUNRISE =
      eppInput -> {
        Optional<LaunchCreateExtension> launchCreate =
            eppInput.getSingleExtension(LaunchCreateExtension.class);
        return launchCreate.isPresent() && !isNullOrEmpty(launchCreate.get().getSignedMarks());
      };

  private static final Predicate<EppInput> IS_IDN =
      eppInput ->
          ((DomainCommand.Create)
                  ((ResourceCommandWrapper) eppInput.getCommandWrapper().getCommand())
                      .getResourceCommand())
              .getFullyQualifiedDomainName()
              .startsWith(ACE_PREFIX);
  private static final Predicate<EppInput> IS_SUBORDINATE =
      eppInput ->
          !isNullOrEmpty(
              ((HostCommand.Create)
                      ((ResourceCommandWrapper) eppInput.getCommandWrapper().getCommand())
                          .getResourceCommand())
                  .getInetAddresses());
  /** Enum defining the distinct statistics (types of registrar actions) to record. */
  public enum StatType {
    CONTACT_CREATES(0, equalTo(Type.CONTACT_CREATE)),
    CONTACT_DELETES(0, equalTo(Type.CONTACT_DELETE)),
    CONTACT_TRANSFER_APPROVES(0, equalTo(Type.CONTACT_TRANSFER_APPROVE)),
    CONTACT_TRANSFER_CANCELS(0, equalTo(Type.CONTACT_TRANSFER_CANCEL)),
    CONTACT_TRANSFER_REJECTS(0, equalTo(Type.CONTACT_TRANSFER_REJECT)),
    CONTACT_TRANSFER_REQUESTS(0, equalTo(Type.CONTACT_TRANSFER_REQUEST)),
    CONTACT_UPDATES(0, equalTo(Type.CONTACT_UPDATE)),
    DOMAIN_APPLICATION_CREATES(0, equalTo(Type.DOMAIN_APPLICATION_CREATE)),
    DOMAIN_APPLICATION_CREATES_LANDRUSH(
        0, equalTo(Type.DOMAIN_APPLICATION_CREATE), IS_SUNRISE.negate()),
    DOMAIN_APPLICATION_CREATES_SUNRISE(0, equalTo(Type.DOMAIN_APPLICATION_CREATE), IS_SUNRISE),
    DOMAIN_APPLICATION_DELETES(0, equalTo(Type.DOMAIN_APPLICATION_DELETE)),
    DOMAIN_APPLICATION_UPDATES(0, equalTo(Type.DOMAIN_APPLICATION_UPDATE)),
    DOMAIN_AUTORENEWS(0, equalTo(Type.DOMAIN_AUTORENEW)),
    DOMAIN_CREATES(0, equalTo(Type.DOMAIN_CREATE)),
    DOMAIN_CREATES_ASCII(1, equalTo(Type.DOMAIN_CREATE), IS_IDN.negate()),
    DOMAIN_CREATES_IDN(1, equalTo(Type.DOMAIN_CREATE), IS_IDN),
    DOMAIN_CREATES_START_DATE_SUNRISE(1, equalTo(Type.DOMAIN_CREATE), IS_SUNRISE),
    DOMAIN_CREATES_WITH_CLAIMS_NOTICE(1, equalTo(Type.DOMAIN_CREATE), HAS_CLAIMS_NOTICE),
    DOMAIN_CREATES_WITH_FEE(
        1,
        equalTo(Type.DOMAIN_CREATE),
        eppInput -> eppInput.getSingleExtension(FeeCreateCommandExtension.class).isPresent()),
    DOMAIN_CREATES_WITH_SEC_DNS(1, equalTo(Type.DOMAIN_CREATE), HAS_SEC_DNS),
    DOMAIN_CREATES_WITHOUT_SEC_DNS(0, equalTo(Type.DOMAIN_CREATE), HAS_SEC_DNS.negate()),
    DOMAIN_DELETES(2, equalTo(Type.DOMAIN_DELETE)),
    DOMAIN_RENEWS(0, equalTo(Type.DOMAIN_RENEW)),
    DOMAIN_RESTORES(1, equalTo(Type.DOMAIN_RESTORE)),
    DOMAIN_TRANSFER_APPROVES(1, equalTo(Type.DOMAIN_TRANSFER_APPROVE)),
    DOMAIN_TRANSFER_CANCELS(1, equalTo(Type.DOMAIN_TRANSFER_CANCEL)),
    DOMAIN_TRANSFER_REJECTS(1, equalTo(Type.DOMAIN_TRANSFER_REJECT)),
    DOMAIN_TRANSFER_REQUESTS(1, equalTo(Type.DOMAIN_TRANSFER_REQUEST)),
    DOMAIN_UPDATES(0, equalTo(Type.DOMAIN_UPDATE)),
    DOMAIN_UPDATES_WITH_SEC_DNS(1, equalTo(Type.DOMAIN_UPDATE), HAS_SEC_DNS),
    DOMAIN_UPDATES_WITHOUT_SEC_DNS(0, equalTo(Type.DOMAIN_UPDATE), HAS_SEC_DNS.negate()),
    HOST_CREATES(0, equalTo(Type.HOST_CREATE)),
    HOST_CREATES_EXTERNAL(0, equalTo(Type.HOST_CREATE), IS_SUBORDINATE.negate()),
    HOST_CREATES_SUBORDINATE(1, equalTo(Type.HOST_CREATE), IS_SUBORDINATE),
    HOST_DELETES(1, equalTo(Type.HOST_DELETE)),
    HOST_UPDATES(1, equalTo(Type.HOST_UPDATE)),
    UNCLASSIFIED_FLOWS(0, Predicates.alwaysFalse());

    /** The number of StatTypes with a non-zero requirement. */
    private static final int NUM_REQUIREMENTS =
        (int) Stream.of(values()).filter(statType -> statType.requirement > 0).count();

    /** Required number of times registrars must complete this action. */
    final int requirement;

    /** Filter to check the HistoryEntry Type */
    @SuppressWarnings("ImmutableEnumChecker") // Predicates are immutable.
    private final Predicate<HistoryEntry.Type> typeFilter;

    /** Optional filter on the EppInput. */
    @SuppressWarnings("ImmutableEnumChecker") // Predicates are immutable.
    private final Optional<Predicate<EppInput>> eppInputFilter;

    StatType(int requirement, Predicate<HistoryEntry.Type> typeFilter) {
      this(requirement, typeFilter, null);
    }

    StatType(
        int requirement,
        Predicate<HistoryEntry.Type> typeFilter,
        Predicate<EppInput> eppInputFilter) {
      this.requirement = requirement;
      this.typeFilter = typeFilter;
      if (eppInputFilter == null) {
        this.eppInputFilter = Optional.empty();
      } else {
        this.eppInputFilter = Optional.of(eppInputFilter);
      }
    }

    /** Returns a more human-readable translation of the enum constant. */
    String description() {
      return Ascii.toLowerCase(this.name().replace('_', ' '));
    }

    /**
     * Check if the {@link HistoryEntry} type matches as well as the {@link EppInput} if supplied.
     */
    boolean matches(HistoryEntry.Type historyType, Optional<EppInput> eppInput) {
      if (eppInputFilter.isPresent() && eppInput.isPresent()) {
        return typeFilter.test(historyType) && eppInputFilter.get().test(eppInput.get());
      } else {
        return typeFilter.test(historyType);
      }
    }
  }

  /** Class to represent stats derived from HistoryEntry objects on actions taken by registrars. */
  static class HistoryEntryStats {

    /** Stores counts of how many times each action type was performed. */
    Multiset<StatType> statCounts = HashMultiset.create();

    /**
     * Records data in the passed historyEntryStats object on what actions have been performed by
     * the four numbered OT&amp;E variants of the registrar name.
     */
    HistoryEntryStats recordRegistrarHistory(String registrarName) {
      ImmutableList.Builder<String> clientIds = new ImmutableList.Builder<>();
      for (int i = 1; i <= 4; i++) {
        clientIds.add(String.format("%s-%d", registrarName, i));
      }
      for (HistoryEntry historyEntry :
          ofy().load().type(HistoryEntry.class).filter("clientId in", clientIds.build()).list()) {
        try {
          record(historyEntry);
        } catch (EppException e) {
          throw new RuntimeException(e);
        }
      }
      return this;
    }

    /** Interprets the data in the provided HistoryEntry and increments counters. */
    void record(final HistoryEntry historyEntry) throws EppException {
      byte[] xmlBytes = historyEntry.getXmlBytes();
      // xmlBytes can be null on contact create and update for safe-harbor compliance.
      final Optional<EppInput> eppInput =
          (xmlBytes == null)
              ? Optional.empty()
              : Optional.of(unmarshal(EppInput.class, xmlBytes));
      if (!statCounts.addAll(
          EnumSet.allOf(StatType.class)
              .stream()
              .filter(statType -> statType.matches(historyEntry.getType(), eppInput))
              .collect(toImmutableList()))) {
        statCounts.add(StatType.UNCLASSIFIED_FLOWS);
      }
    }

    /**
     * Returns a list of failure messages describing any cases where the passed stats fail to meet
     * the required thresholds, or the empty list if all requirements are met.
     */
    List<String> findFailures() {
      List<String> messages = new ArrayList<>();
      for (StatType statType : StatType.values()) {
        if (statCounts.count(statType) < statType.requirement) {
          messages.add(
              String.format(
                  "Failure: %s %s found.",
                  (statType.requirement == 1 ? "No" : "Not enough"), statType.description()));
        }
      }
      return messages;
    }

    /** Returns a string showing all possible actions and how many times each was performed. */
    @Override
    public String toString() {
      return String.format(
          "%s\nTOTAL: %d",
          EnumSet.allOf(StatType.class)
              .stream()
              .map(stat -> String.format("%s: %d", stat.description(), statCounts.count(stat)))
              .collect(Collectors.joining("\n")),
          statCounts.size());
    }

    /** Returns a string showing the results of each test, one character per test. */
    String toSummary() {
      return EnumSet.allOf(StatType.class)
          .stream()
          .filter(statType -> statType.requirement > 0)
          .sorted()
          .map(statType -> (statCounts.count(statType) < statType.requirement) ? "." : "-")
          .collect(Collectors.joining(""));
    }
  }
}
