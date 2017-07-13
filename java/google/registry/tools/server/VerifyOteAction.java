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
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Maps.toMap;
import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.isNullOrEmpty;
import static google.registry.util.DomainNameUtils.ACE_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
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
import google.registry.request.auth.AuthLevel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * A servlet that verifies a registrar's OTE status. Note that this is eventually consistent, so
 * OT&amp;E commands that have been run just previously to verification may not be picked up yet.
 */
@Action(
  path = VerifyOteAction.PATH,
  method = Action.Method.POST,
  auth =
      @Auth(
        methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API},
        minimumLevel = AuthLevel.APP,
        userPolicy = Auth.UserPolicy.ADMIN
      )
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
        (List<String>) json.get("registrars"),
        new Function<String, Object>() {
          @Nonnull
          @Override
          public Object apply(@Nonnull String registrar) {
            return checkRegistrar(registrar, summarize);
          }
        });
  }

  /** Checks whether the provided registrar has passed OT&amp;E and returns relevant information. */
  private String checkRegistrar(String registrarName, boolean summarize) {
    HistoryEntryStats historyEntryStats =
        new HistoryEntryStats().recordRegistrarHistory(registrarName);
    List<String> failureMessages = historyEntryStats.findFailures();
    String passedFraction =
        String.format(
            "%2d/%2d",
            StatType.NUM_REQUIREMENTS - failureMessages.size(), StatType.NUM_REQUIREMENTS);
    String status = failureMessages.isEmpty() ? "PASS" : "FAIL";
    return summarize
        ? String.format(
            "Num actions: %4d - Reqs passed: %s - Overall: %s",
            historyEntryStats.statCounts.size(), passedFraction, status)
        : String.format(
            "%s\n%s\nRequirements passed: %s\nOverall OT&E status: %s\n",
            historyEntryStats, Joiner.on('\n').join(failureMessages), passedFraction, status);
  }

  private static final Predicate<EppInput> HAS_CLAIMS_NOTICE =
      new Predicate<EppInput>() {
        @Override
        public boolean apply(@Nonnull EppInput eppInput) {
          LaunchCreateExtension launchCreate =
              eppInput.getSingleExtension(LaunchCreateExtension.class);
          return launchCreate != null && launchCreate.getNotice() != null;
        }
      };

  private static final Predicate<EppInput> HAS_FEE =
      new Predicate<EppInput>() {
        @Override
        public boolean apply(@Nonnull EppInput eppInput) {
          return eppInput.getSingleExtension(FeeCreateCommandExtension.class) != null;
        }
      };

  private static final Predicate<EppInput> HAS_SEC_DNS =
      new Predicate<EppInput>() {
        @Override
        public boolean apply(@Nonnull EppInput eppInput) {
          return (eppInput.getSingleExtension(SecDnsCreateExtension.class) != null)
              || (eppInput.getSingleExtension(SecDnsUpdateExtension.class) != null);
        }
      };

  private static final Predicate<EppInput> IS_SUNRISE =
      new Predicate<EppInput>() {
        @Override
        public boolean apply(@Nonnull EppInput eppInput) {
          LaunchCreateExtension launchCreate =
              eppInput.getSingleExtension(LaunchCreateExtension.class);
          return launchCreate != null && !isNullOrEmpty(launchCreate.getSignedMarks());
        }
      };

  private static final Predicate<EppInput> IS_IDN =
      new Predicate<EppInput>() {
        @Override
        public boolean apply(@Nonnull EppInput eppInput) {
          return ((DomainCommand.Create)
                  ((ResourceCommandWrapper) eppInput.getCommandWrapper().getCommand())
                      .getResourceCommand())
              .getFullyQualifiedDomainName()
              .startsWith(ACE_PREFIX);
        }
      };

  private static final Predicate<EppInput> IS_SUBORDINATE =
      new Predicate<EppInput>() {
        @Override
        public boolean apply(@Nonnull EppInput eppInput) {
          return !isNullOrEmpty(
              ((HostCommand.Create)
                      ((ResourceCommandWrapper) eppInput.getCommandWrapper().getCommand())
                          .getResourceCommand())
                  .getInetAddresses());
        }
      };

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
        1, equalTo(Type.DOMAIN_APPLICATION_CREATE), not(IS_SUNRISE)),
    DOMAIN_APPLICATION_CREATES_SUNRISE(1, equalTo(Type.DOMAIN_APPLICATION_CREATE), IS_SUNRISE),
    DOMAIN_APPLICATION_DELETES(2, equalTo(Type.DOMAIN_APPLICATION_DELETE)),
    DOMAIN_APPLICATION_UPDATES(2, equalTo(Type.DOMAIN_APPLICATION_UPDATE)),
    DOMAIN_CREATES(0, equalTo(Type.DOMAIN_CREATE)),
    DOMAIN_CREATES_ASCII(1, equalTo(Type.DOMAIN_CREATE), not(IS_IDN)),
    DOMAIN_CREATES_IDN(1, equalTo(Type.DOMAIN_CREATE), IS_IDN),
    DOMAIN_CREATES_WITH_CLAIMS_NOTICE(1, equalTo(Type.DOMAIN_CREATE), HAS_CLAIMS_NOTICE),
    DOMAIN_CREATES_WITH_FEE(1, equalTo(Type.DOMAIN_CREATE), HAS_FEE),
    DOMAIN_CREATES_WITH_SEC_DNS(1, equalTo(Type.DOMAIN_CREATE), HAS_SEC_DNS),
    DOMAIN_CREATES_WITHOUT_SEC_DNS(0, equalTo(Type.DOMAIN_CREATE), not(HAS_SEC_DNS)),
    DOMAIN_DELETES(2, equalTo(Type.DOMAIN_DELETE)),
    DOMAIN_RENEWS(0, equalTo(Type.DOMAIN_RENEW)),
    DOMAIN_RESTORES(1, equalTo(Type.DOMAIN_RESTORE)),
    DOMAIN_TRANSFER_APPROVES(1, equalTo(Type.DOMAIN_TRANSFER_APPROVE)),
    DOMAIN_TRANSFER_CANCELS(1, equalTo(Type.DOMAIN_TRANSFER_CANCEL)),
    DOMAIN_TRANSFER_REJECTS(1, equalTo(Type.DOMAIN_TRANSFER_REJECT)),
    DOMAIN_TRANSFER_REQUESTS(1, equalTo(Type.DOMAIN_TRANSFER_REQUEST)),
    DOMAIN_UPDATES(0, equalTo(Type.DOMAIN_UPDATE)),
    DOMAIN_UPDATES_WITH_SEC_DNS(1, equalTo(Type.DOMAIN_UPDATE), HAS_SEC_DNS),
    DOMAIN_UPDATES_WITHOUT_SEC_DNS(0, equalTo(Type.DOMAIN_UPDATE), not(HAS_SEC_DNS)),
    HOST_CREATES(0, equalTo(Type.HOST_CREATE)),
    HOST_CREATES_EXTERNAL(0, equalTo(Type.HOST_CREATE), not(IS_SUBORDINATE)),
    HOST_CREATES_SUBORDINATE(1, equalTo(Type.HOST_CREATE), IS_SUBORDINATE),
    HOST_DELETES(1, equalTo(Type.HOST_DELETE)),
    HOST_UPDATES(1, equalTo(Type.HOST_UPDATE)),
    UNCLASSIFIED_FLOWS(0, Predicates.<HistoryEntry.Type>alwaysFalse());

    /** The number of StatTypes with a non-zero requirement. */
    private static final int NUM_REQUIREMENTS =
        FluentIterable.from(values())
            .filter(
                new Predicate<StatType>() {
                  @Override
                  public boolean apply(@Nonnull StatType statType) {
                    return statType.requirement > 0;
                  }
                })
            .size();

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
        this.eppInputFilter = Optional.<Predicate<EppInput>>absent();
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
        return typeFilter.apply(historyType) && eppInputFilter.get().apply(eppInput.get());
      } else {
        return typeFilter.apply(historyType);
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
              ? Optional.<EppInput>absent()
              : Optional.of(unmarshal(EppInput.class, xmlBytes));
      if (!statCounts.addAll(
          FluentIterable.from(EnumSet.allOf(StatType.class))
              .filter(
                  new Predicate<StatType>() {
                    @Override
                    public boolean apply(@Nonnull StatType statType) {
                      return statType.matches(historyEntry.getType(), eppInput);
                    }
                  })
              .toList())) {
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
      return FluentIterable.from(EnumSet.allOf(StatType.class))
          .transform(
              new Function<StatType, String>() {
                @Nonnull
                @Override
                public String apply(@Nonnull StatType statType) {
                  return String.format(
                      "%s: %d", statType.description(), statCounts.count(statType));
                }
              })
          .append(String.format("TOTAL: %d", statCounts.size()))
          .join(Joiner.on("\n"));
    }
  }
}
