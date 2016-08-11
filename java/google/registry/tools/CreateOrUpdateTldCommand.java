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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.RoidSuffixes.isRoidSuffixUsed;
import static google.registry.util.CollectionUtils.findDuplicates;
import static google.registry.util.DomainNameUtils.canonicalizeDomainName;

import com.beust.jcommander.Parameter;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import google.registry.model.pricing.StaticPremiumListPricingEngine;
import google.registry.model.registry.Registries;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.registry.Registry.TldType;
import google.registry.model.registry.label.PremiumList;
import google.registry.tools.params.OptionalStringParameter;
import google.registry.tools.params.TldStateParameter;
import google.registry.tools.params.TransitionListParameter.BillingCostTransitions;
import google.registry.tools.params.TransitionListParameter.TldStateTransitions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Shared base class for commands to create or update a TLD. */
abstract class CreateOrUpdateTldCommand extends MutatingCommand {

  @Inject
  @Named("dnsWriterNames")
  Set<String> dnsWriterNames;

  @Parameter(description = "Names of the TLDs", required = true)
  List<String> mainParameters;

  @Parameter(
      names = "--escrow",
      description = "Whether to enable nightly RDE escrow deposits",
      arity = 1)
  private Boolean escrow;

  @Parameter(
      names = "--dns",
      description = "Set to false to pause writing to the DNS queue",
      arity = 1)
  private Boolean dns;

  @Nullable
  @Parameter(
      names = "--add_grace_period",
      description = "Length of the add grace period")
  Duration addGracePeriod;

  @Nullable
  @Parameter(
      names = "--redemption_grace_period",
      description = "Length of the redemption grace period")
  Duration redemptionGracePeriod;

  @Nullable
  @Parameter(
      names = "--pending_delete_length",
      description = "Length of the pending delete period")
  Duration pendingDeleteLength;

  @Nullable
  @Parameter(
      names = "--automatic_transfer_length",
      description = "Length of the automatic transfer period")
  private Duration automaticTransferLength;

  @Nullable
  @Parameter(
      names = "--restore_billing_cost",
      description = "One-time billing cost for restoring a domain")
  private Money restoreBillingCost;

  @Nullable
  @Parameter(
      names = "--roid_suffix",
      description = "The suffix to be used for ROIDs, e.g. COM for .com domains (which then "
          + "creates roids looking like 123ABC-COM)")
  String roidSuffix;

  @Nullable
  @Parameter(
      names = "--server_status_change_cost",
      description = "One-time billing cost for a server status change")
  private Money serverStatusChangeCost;

  @Nullable
  @Parameter(
      names = "--tld_type",
      description = "Tld type (REAL or TEST)")
  private TldType tldType;

  @Nullable
  @Parameter(
      names = "--premium_price_ack_required",
      description = "Whether operations on premium domains require explicit ack of prices",
      arity = 1)
  private Boolean premiumPriceAckRequired;

  @Nullable
  @Parameter(
      names = "--create_billing_cost",
      description = "Per-year billing cost for creating a domain")
  Money createBillingCost;

  @Nullable
  @Parameter(
      names = "--drive_folder_id",
      description = "Id of the folder in drive used to publish information for this TLD",
      converter = OptionalStringParameter.class,
      validateWith = OptionalStringParameter.class)
  Optional<String> driveFolderId;

  @Nullable
  @Parameter(
      names = "--lordn_username",
      description = "Username for LORDN uploads",
      converter = OptionalStringParameter.class,
      validateWith = OptionalStringParameter.class)
  Optional<String> lordnUsername;

  @Nullable
  @Parameter(
      names = "--premium_list",
      description = "The name of the premium list to apply to the TLD",
      converter = OptionalStringParameter.class,
      validateWith = OptionalStringParameter.class)
  Optional<String> premiumListName;

  @Parameter(
      names = "--tld_state_transitions",
      converter = TldStateTransitions.class,
      validateWith = TldStateTransitions.class,
      description = "Comma-delimited list of TLD state transitions, of the form "
          + "<time>=<tld-state>[,<time>=<tld-state>]*")
  ImmutableSortedMap<DateTime, TldState> tldStateTransitions = ImmutableSortedMap.of();

  @Parameter(
      names = "--renew_billing_cost_transitions",
      converter = BillingCostTransitions.class,
      validateWith = BillingCostTransitions.class,
      description = "Comma-delimited list of renew billing cost transitions, of the form "
          + "<time>=<money-amount>[,<time>=<money-amount>]* where each amount "
          + "represents the per-year billing cost for renewing a domain")
  ImmutableSortedMap<DateTime, Money> renewBillingCostTransitions =
      ImmutableSortedMap.of();

  @Parameter(
      names = "--eap_fee_schedule",
      converter = BillingCostTransitions.class,
      validateWith = BillingCostTransitions.class,
      description = "Comma-delimited list of EAP fees effective on specific dates, of the form "
          + "<time>=<money-amount>[,<time>=<money-amount>]* where each amount represents the "
          + "EAP fee for creating a new domain under the TLD.")
  ImmutableSortedMap<DateTime, Money> eapFeeSchedule = ImmutableSortedMap.of();

  @Nullable
  @Parameter(
      names = "--reserved_lists",
      description = "A comma-separated list of reserved list names to be applied to the TLD")
  List<String> reservedListNames;

  @Nullable
  @Parameter(
      names = "--allowed_registrants",
      description = "A comma-separated list of allowed registrants for the TLD")
  List<String> allowedRegistrants;

  @Nullable
  @Parameter(
      names = "--allowed_nameservers",
      description = "A comma-separated list of allowed nameservers for the TLD")
  List<String> allowedNameservers;

  @Parameter(
      names = {"-o", "--override_reserved_list_rules"},
      description = "Override restrictions on reserved list naming")
  boolean overrideReservedListRules;

  @Nullable
  @Parameter(
      names = "--claims_period_end",
      description = "The end of the claims period")
  DateTime claimsPeriodEnd;

  @Nullable
  @Parameter(
    names = "--dns_writer",
    description = "The name of the DnsWriter implementation to use",
    converter = OptionalStringParameter.class,
    validateWith = OptionalStringParameter.class)
  Optional<String> dnsWriter;

  @Nullable
  @Parameter(
      names = "--lrp_tld_states",
      converter = TldStateParameter.class,
      description = "A comma-separated list of TLD states for which LRP is available")
  List<TldState> lrpTldStates;

  /** Returns the existing registry (for update) or null (for creates). */
  @Nullable
  abstract Registry getOldRegistry(String tld);

  abstract ImmutableSet<String> getAllowedRegistrants(Registry oldRegistry);

  abstract ImmutableSet<String> getAllowedNameservers(Registry oldRegistry);

  abstract ImmutableSet<String> getReservedLists(Registry oldRegistry);

  abstract Optional<Map.Entry<DateTime, TldState>> getTldStateTransitionToAdd();

  /** Subclasses can override this to set their own properties. */
  void setCommandSpecificProperties(@SuppressWarnings("unused") Registry.Builder builder) {}

  /** Subclasses can override this to assert that the command can be run in this environment. */
  void assertAllowedEnvironment() {}

  protected abstract void initTldCommand() throws Exception;

  @Override
  protected final void init() throws Exception {
    assertAllowedEnvironment();
    initTldCommand();
    String duplicates = Joiner.on(", ").join(findDuplicates(mainParameters));
    checkArgument(duplicates.isEmpty(), "Duplicate arguments found: \"%s\"", duplicates);
    Set<String> tlds = ImmutableSet.copyOf(mainParameters);
    checkArgument(roidSuffix == null || tlds.size() == 1,
        "Can't update roid suffixes on multiple TLDs simultaneously");
    for (String tld : tlds) {
      checkArgument(tld.equals(canonicalizeDomainName(tld)));
      checkArgument(
          !CharMatcher.javaDigit().matches(tld.charAt(0)),
          "TLDs cannot begin with a number.");
      Registry oldRegistry = getOldRegistry(tld);
      if (roidSuffix != null) {
        checkArgument(
            !isRoidSuffixUsed(roidSuffix)
                || (oldRegistry != null && roidSuffix.equals(oldRegistry.getRoidSuffix())),
            "The roid suffix %s is already in use",
            roidSuffix);
      }
      // TODO(b/26901539): Add a flag to set the pricing engine once we have more than one option.
      Registry.Builder builder =
          oldRegistry == null
              ? new Registry.Builder()
                  .setTldStr(tld)
                  .setPremiumPricingEngine(StaticPremiumListPricingEngine.NAME)
              : oldRegistry.asBuilder();

      if (escrow != null) {
        builder.setEscrowEnabled(escrow);
      }

      if (dns != null) {
        builder.setDnsPaused(!dns);
      }

      Optional<Map.Entry<DateTime, TldState>> tldStateTransitionToAdd =
          getTldStateTransitionToAdd();
      if (!tldStateTransitions.isEmpty()) {
        builder.setTldStateTransitions(tldStateTransitions);
      } else if (tldStateTransitionToAdd.isPresent()) {
        ImmutableSortedMap.Builder<DateTime, TldState> newTldStateTransitions =
            ImmutableSortedMap.naturalOrder();
        if (oldRegistry != null) {
          checkArgument(
              oldRegistry.getTldStateTransitions().lastKey().isBefore(
                  tldStateTransitionToAdd.get().getKey()),
              "Cannot add %s at %s when there is a later transition already scheduled",
              tldStateTransitionToAdd.get().getValue(),
              tldStateTransitionToAdd.get().getKey());
          newTldStateTransitions.putAll(oldRegistry.getTldStateTransitions());
        }
        builder.setTldStateTransitions(
            newTldStateTransitions.put(getTldStateTransitionToAdd().get()).build());
      }

      if (!renewBillingCostTransitions.isEmpty()) {
        // TODO(b/20764952): need invoicing support for multiple renew billing costs.
        if (renewBillingCostTransitions.size() > 1) {
          System.err.println(
              "----------------------\n"
              + "WARNING: Do not set multiple renew cost transitions until b/20764952 is fixed.\n"
              + "----------------------\n");
        }
        builder.setRenewBillingCostTransitions(renewBillingCostTransitions);
      }

      if (!eapFeeSchedule.isEmpty()) {
        builder.setEapFeeSchedule(eapFeeSchedule);
      }

      if (addGracePeriod != null) {
        builder.setAddGracePeriodLength(addGracePeriod);
      }

      if (redemptionGracePeriod != null) {
        builder.setRedemptionGracePeriodLength(redemptionGracePeriod);
      }

      if (pendingDeleteLength != null) {
        builder.setPendingDeleteLength(pendingDeleteLength);
      }

      if (automaticTransferLength != null) {
        builder.setAutomaticTransferLength(automaticTransferLength);
      }

      if (driveFolderId != null) {
        builder.setDriveFolderId(driveFolderId.orNull());
      }

      if (createBillingCost != null) {
        builder.setCreateBillingCost(createBillingCost);
      }

      if (restoreBillingCost != null) {
        builder.setRestoreBillingCost(restoreBillingCost);
      }

      if (roidSuffix != null) {
        builder.setRoidSuffix(roidSuffix);
      }

      if (serverStatusChangeCost != null) {
        builder.setServerStatusChangeBillingCost(serverStatusChangeCost);
      }

      if (tldType != null) {
        builder.setTldType(tldType);
      }

      if (premiumPriceAckRequired != null) {
        builder.setPremiumPriceAckRequired(premiumPriceAckRequired);
      }

      if (lordnUsername != null) {
        builder.setLordnUsername(lordnUsername.orNull());
      }

      if (claimsPeriodEnd != null) {
        builder.setClaimsPeriodEnd(claimsPeriodEnd);
      }

      if (premiumListName != null) {
        if (premiumListName.isPresent()) {
          Optional<PremiumList> premiumList = PremiumList.get(premiumListName.get());
          checkArgument(premiumList.isPresent(),
              String.format("The premium list '%s' doesn't exist", premiumListName.get()));
          builder.setPremiumList(premiumList.get());
        } else {
          builder.setPremiumList(null);
        }
      }

      if (dnsWriter != null) {
        if (dnsWriter.isPresent()) {
          checkArgument(
              dnsWriterNames.contains(dnsWriter.get()),
              "The DNS writer '%s' doesn't exist",
              dnsWriter.get());
          builder.setDnsWriter(dnsWriter.get());
        }
      }

      if (lrpTldStates != null) {
        builder.setLrpTldStates(ImmutableSet.copyOf(lrpTldStates));
      }

      ImmutableSet<String> newReservedListNames = getReservedLists(oldRegistry);
      checkReservedListValidityForTld(tld, newReservedListNames);
      builder.setReservedListsByName(newReservedListNames);

      builder.setAllowedRegistrantContactIds(getAllowedRegistrants(oldRegistry));

      builder.setAllowedFullyQualifiedHostNames(getAllowedNameservers(oldRegistry));

      // Update the Registry object.
      setCommandSpecificProperties(builder);
      stageEntityChange(oldRegistry, builder.build());
    }
  }

  @Override
  public String execute() throws Exception {
    try {
      return super.execute();
    } finally {
      // Manually reset the cache here so that subsequent commands (e.g. in SetupOteCommand) see
      // the latest version of the data.
      // TODO(b/24903801): change all those places to use uncached code paths to get Registries.
      Registries.resetCache();
    }
  }

  private void checkReservedListValidityForTld(String tld, Set<String> reservedListNames) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
    for (String reservedListName : reservedListNames) {
      if (!reservedListName.startsWith("common_") && !reservedListName.startsWith(tld + "_")) {
        builder.add(reservedListName);
      }
    }
    ImmutableList<String> invalidNames = builder.build();
    if (!invalidNames.isEmpty()) {
      String errMsg = String.format("The reserved list(s) %s cannot be applied to the tld %s",
          Joiner.on(", ").join(invalidNames),
          tld);
      if (overrideReservedListRules) {
        System.err.println("Error overriden: " + errMsg);
      } else {
        throw new IllegalArgumentException(errMsg);
      }
    }
  }
}
