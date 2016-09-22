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

package google.registry.flows.domain;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.pricing.PricingEngineProxy.getPricesForDomainName;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.ResourceMutateFlow.ResourceDoesNotExistException;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.DomainCommand.Create;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.LrpToken;
import google.registry.model.domain.fee.BaseFee;
import google.registry.model.domain.fee.BaseFee.FeeType;
import google.registry.model.domain.fee.Credit;
import google.registry.model.domain.fee.Fee;
import google.registry.model.eppinput.EppInput;
import google.registry.model.pricing.PremiumPricingEngine.DomainPrices;
import google.registry.model.registry.Registry;
import java.util.List;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * Provides pricing, billing, and update logic, with call-outs that can be customized by providing
 * implementations on a per-TLD basis.
 */
public final class TldSpecificLogicProxy {
  /** A collection of fees and credits for a specific EPP transform. */
  public static final class EppCommandOperations extends ImmutableObject {
    private final CurrencyUnit currency;
    private final ImmutableList<Fee> fees;
    private final ImmutableList<Credit> credits;

    /** Constructs an EppCommandOperations object using separate lists of fees and credits. */
    EppCommandOperations(
        CurrencyUnit currency, ImmutableList<Fee> fees, ImmutableList<Credit> credits) {
      this.currency = checkArgumentNotNull(
          currency, "Currency may not be null in EppCommandOperations.");
      checkArgument(!fees.isEmpty(), "You must specify one or more fees.");
      this.fees = checkArgumentNotNull(fees, "Fees may not be null in EppCommandOperations.");
      this.credits =
          checkArgumentNotNull(credits, "Credits may not be null in EppCommandOperations.");
    }

    /**
     * Constructs an EppCommandOperations object. The arguments are sorted into fees and credits.
     */
    EppCommandOperations(CurrencyUnit currency, BaseFee... feesAndCredits) {
      this.currency = checkArgumentNotNull(
          currency, "Currency may not be null in EppCommandOperations.");
      ImmutableList.Builder<Fee> feeBuilder = new ImmutableList.Builder<>();
      ImmutableList.Builder<Credit> creditBuilder = new ImmutableList.Builder<>();
      for (BaseFee feeOrCredit : feesAndCredits) {
        if (feeOrCredit instanceof Credit) {
          creditBuilder.add((Credit) feeOrCredit);
        } else {
          feeBuilder.add((Fee) feeOrCredit);
        }
      }
      this.fees = feeBuilder.build();
      this.credits = creditBuilder.build();
    }

    private Money getTotalCostForType(FeeType type) {
      Money result = Money.zero(currency);
      checkArgumentNotNull(type);
      for (Fee fee : fees) {
        if (fee.getType() == type) {
          result = result.plus(fee.getCost());
        }
      }
      return result;
    }

    /** Returns the total cost of all fees and credits for the event. */
    public Money getTotalCost() {
      Money result = Money.zero(currency);
      for (Fee fee : fees) {
        result = result.plus(fee.getCost());
      }
      for (Credit credit : credits) {
        result = result.plus(credit.getCost());
      }
      return result;
    }

    /** Returns the create cost for the event. */
    public Money getCreateCost() {
      return getTotalCostForType(FeeType.CREATE);
    }

    /** Returns the EAP cost for the event. */
    public Money getEapCost() {
      return getTotalCostForType(FeeType.EAP);
    }

    /** Returns the list of fees for the event. */
    public ImmutableList<Fee> getFees() {
      return fees;
    }

    /** Returns the list of credits for the event. */
    public List<Credit> getCredits() {
      return nullToEmpty(credits);
    }

    /** Returns the currency for all fees in the event. */
    public final CurrencyUnit getCurrency() {
      return currency;
    }
  }

  private TldSpecificLogicProxy() {}

  /** Returns a new create price for the Pricer. */
  public static EppCommandOperations getCreatePrice(
      Registry registry,
      String domainName,
      String clientId,
      DateTime date,
      int years,
      EppInput eppInput) throws EppException {
    CurrencyUnit currency = registry.getCurrency();

    // Get the create cost, either from the extra flow logic or straight from PricingEngineProxy.
    BaseFee createFeeOrCredit;
    Optional<RegistryExtraFlowLogic> extraFlowLogic =
        RegistryExtraFlowLogicProxy.newInstanceForTld(registry.getTldStr());
    if (extraFlowLogic.isPresent()) {
      createFeeOrCredit = extraFlowLogic.get()
          .getCreateFeeOrCredit(domainName, clientId, date, years, eppInput);
    } else {
      DomainPrices prices = getPricesForDomainName(domainName, date);
      createFeeOrCredit =
          Fee.create(prices.getCreateCost().multipliedBy(years).getAmount(), FeeType.CREATE);
    }

    // Create fees for the cost and the EAP fee, if any.
    Fee eapFee = registry.getEapFeeFor(date);
    if (!eapFee.hasZeroCost()) {
      return new EppCommandOperations(currency, createFeeOrCredit, eapFee);
    } else {
      return new EppCommandOperations(currency, createFeeOrCredit);
    }
  }

  /**
   * Computes the renew fee or credit. This is called by other methods which use the renew fee
   * (renew, restore, etc).
   */
  static BaseFee getRenewFeeOrCredit(
      Registry registry,
      String domainName,
      String clientId,
      DateTime date,
      int years,
      EppInput eppInput) throws EppException {
    Optional<RegistryExtraFlowLogic> extraFlowLogic =
        RegistryExtraFlowLogicProxy.newInstanceForTld(registry.getTldStr());
    if (extraFlowLogic.isPresent()) {
      // TODO: Consider changing the method definition to have the domain passed in to begin with.
      DomainResource domain = loadByForeignKey(DomainResource.class, domainName, date);
      if (domain == null) {
        throw new ResourceDoesNotExistException(DomainResource.class, domainName);
      }
      return
          extraFlowLogic.get().getRenewFeeOrCredit(domain, clientId, date, years, eppInput);
    } else {
      DomainPrices prices = getPricesForDomainName(domainName, date);
      return Fee.create(prices.getRenewCost().multipliedBy(years).getAmount(), FeeType.RENEW);
    }
  }

  /** Returns a new renew price for the pricer. */
  public static EppCommandOperations getRenewPrice(
      Registry registry,
      String domainName,
      String clientId,
      DateTime date,
      int years,
      EppInput eppInput) throws EppException {
    return new EppCommandOperations(
        registry.getCurrency(),
        getRenewFeeOrCredit(registry, domainName, clientId, date, years, eppInput));
  }

  /** Returns a new restore price for the pricer. */
  public static EppCommandOperations getRestorePrice(
      Registry registry,
      String domainName,
      String clientId,
      DateTime date,
      EppInput eppInput) throws EppException {
    return new EppCommandOperations(
        registry.getCurrency(),
        getRenewFeeOrCredit(registry, domainName, clientId, date, 1, eppInput),
        Fee.create(registry.getStandardRestoreCost().getAmount(), FeeType.RESTORE));
  }

  /** Returns a new transfer price for the pricer. */
  public static EppCommandOperations getTransferPrice(
      Registry registry,
      String domainName,
      String clientId,
      DateTime transferDate,
      int years,
      EppInput eppInput) throws EppException {
    // Currently, all transfer prices = renew prices, so just pass through.
    return getRenewPrice(
        registry, domainName, clientId, transferDate, years, eppInput);
  }

  /** Returns a new update price for the pricer. */
  public static EppCommandOperations getUpdatePrice(
      Registry registry,
      String domainName,
      String clientId,
      DateTime date,
      EppInput eppInput) throws EppException {
    CurrencyUnit currency = registry.getCurrency();

    // If there is extra flow logic, it may specify an update price. Otherwise, there is none.
    BaseFee feeOrCredit;
    Optional<RegistryExtraFlowLogic> extraFlowLogic =
        RegistryExtraFlowLogicProxy.newInstanceForTld(registry.getTldStr());
    if (extraFlowLogic.isPresent()) {
      // TODO: Consider changing the method definition to have the domain passed in to begin with.
      DomainResource domain = loadByForeignKey(DomainResource.class, domainName, date);
      if (domain == null) {
        throw new ResourceDoesNotExistException(DomainResource.class, domainName);
      }
      feeOrCredit =
          extraFlowLogic.get().getUpdateFeeOrCredit(domain, clientId, date, eppInput);
    } else {
      feeOrCredit = Fee.create(Money.zero(registry.getCurrency()).getAmount(), FeeType.UPDATE);
    }
    
    return new EppCommandOperations(currency, feeOrCredit);
  }

  /** Returns the fee class for a given domain and date. */
  public static Optional<String> getFeeClass(String domainName, DateTime date) {
    return getPricesForDomainName(domainName, date).getFeeClass();
  }

  /**
   * Checks whether a {@link Create} command has a valid {@link LrpToken} for a particular TLD, and
   * return that token (wrapped in an {@link Optional}) if one exists.
   * 
   * <p>This method has no knowledge of whether or not an auth code (interpreted here as an LRP
   * token) has already been checked against the reserved list for QLP (anchor tenant), as auth
   * codes are used for both types of registrations.
   */
  public static Optional<LrpToken> getMatchingLrpToken(Create createCommand, String tld) {
    // Note that until the actual per-TLD logic is built out, what's being done here is a basic
    // domain-name-to-assignee match.
    String lrpToken = createCommand.getAuthInfo().getPw().getValue();
    LrpToken token = ofy().load().key(Key.create(LrpToken.class, lrpToken)).now();
    if (token != null) {
      if (token.getAssignee().equalsIgnoreCase(createCommand.getFullyQualifiedDomainName())
          && token.getRedemptionHistoryEntry() == null
          && token.getValidTlds().contains(tld)) {
        return Optional.of(token);
      }
    }
    return Optional.<LrpToken>absent();
  }
}
