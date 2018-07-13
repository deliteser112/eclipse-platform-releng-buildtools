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

package google.registry.flows.domain;

import static google.registry.flows.FlowUtils.persistEntityChanges;
import static google.registry.flows.FlowUtils.validateClientIsLoggedIn;
import static google.registry.flows.ResourceFlowUtils.verifyResourceDoesNotExist;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.cloneAndLinkReferences;
import static google.registry.flows.domain.DomainFlowUtils.createFeeCreateResponse;
import static google.registry.flows.domain.DomainFlowUtils.prepareMarkedLrpTokenEntity;
import static google.registry.flows.domain.DomainFlowUtils.validateCreateCommandContactsAndNameservers;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainAllowedOnCreateRestrictedTld;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainName;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainNameWithIdnTables;
import static google.registry.flows.domain.DomainFlowUtils.validateFeeChallenge;
import static google.registry.flows.domain.DomainFlowUtils.validateLaunchCreateNotice;
import static google.registry.flows.domain.DomainFlowUtils.validateRegistrationPeriod;
import static google.registry.flows.domain.DomainFlowUtils.validateSecDnsExtension;
import static google.registry.flows.domain.DomainFlowUtils.verifyClaimsNoticeIfAndOnlyIfNeeded;
import static google.registry.flows.domain.DomainFlowUtils.verifyClaimsPeriodNotEnded;
import static google.registry.flows.domain.DomainFlowUtils.verifyLaunchPhaseMatchesRegistryPhase;
import static google.registry.flows.domain.DomainFlowUtils.verifyNoCodeMarks;
import static google.registry.flows.domain.DomainFlowUtils.verifyNotReserved;
import static google.registry.flows.domain.DomainFlowUtils.verifyPremiumNameIsNotBlocked;
import static google.registry.flows.domain.DomainFlowUtils.verifyRegistrarIsActive;
import static google.registry.flows.domain.DomainFlowUtils.verifyUnitIsYears;
import static google.registry.model.EppResourceUtils.createDomainRepoId;
import static google.registry.model.eppcommon.StatusValue.SERVER_TRANSFER_PROHIBITED;
import static google.registry.model.eppcommon.StatusValue.SERVER_UPDATE_PROHIBITED;
import static google.registry.model.index.DomainApplicationIndex.loadActiveApplicationsByDomainName;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registry.TldState.GENERAL_AVAILABILITY;
import static google.registry.model.registry.Registry.TldState.START_DATE_SUNRISE;
import static google.registry.model.registry.Registry.TldState.SUNRISE;
import static google.registry.model.registry.Registry.TldState.SUNRUSH;
import static google.registry.model.registry.label.ReservedList.matchesAnchorTenantReservation;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.leapSafeAddYears;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Key;
import google.registry.dns.DnsQueue;
import google.registry.flows.EppException;
import google.registry.flows.EppException.CommandUseErrorException;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.flows.ExtensionManager;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.Superuser;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.annotations.ReportingSpec;
import google.registry.flows.custom.DomainCreateFlowCustomLogic;
import google.registry.flows.custom.DomainCreateFlowCustomLogic.BeforeResponseParameters;
import google.registry.flows.custom.DomainCreateFlowCustomLogic.BeforeResponseReturnData;
import google.registry.flows.custom.EntityChanges;
import google.registry.flows.domain.token.AllocationTokenFlowUtils;
import google.registry.model.ImmutableObject;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.OneTime;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.billing.BillingEvent.Recurring;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainCommand;
import google.registry.model.domain.DomainCommand.Create;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.Period;
import google.registry.model.domain.fee.FeeCreateCommandExtension;
import google.registry.model.domain.fee.FeeTransformResponseExtension;
import google.registry.model.domain.launch.LaunchCreateExtension;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.domain.secdns.SecDnsCreateExtension;
import google.registry.model.domain.token.AllocationToken;
import google.registry.model.domain.token.AllocationTokenExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.CreateData.DomainCreateData;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.ofy.ObjectifyService;
import google.registry.model.poll.PollMessage;
import google.registry.model.poll.PollMessage.Autorenew;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.registry.Registry.TldType;
import google.registry.model.reporting.DomainTransactionRecord;
import google.registry.model.reporting.DomainTransactionRecord.TransactionReportField;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.reporting.IcannReportingTypes.ActivityReportField;
import google.registry.tmch.LordnTask;
import java.util.Optional;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * An EPP flow that creates a new domain resource.
 *
 * @error {@link google.registry.flows.domain.token.AllocationTokenFlowUtils.AlreadyRedeemedAllocationTokenException}
 * @error {@link google.registry.flows.domain.token.AllocationTokenFlowUtils.InvalidAllocationTokenException}
 * @error {@link google.registry.flows.exceptions.OnlyToolCanPassMetadataException}
 * @error {@link google.registry.flows.exceptions.ResourceAlreadyExistsException}
 * @error {@link google.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link google.registry.flows.ExtensionManager.UndeclaredServiceExtensionException}
 * @error {@link DomainCreateFlow.AnchorTenantCreatePeriodException}
 * @error {@link DomainCreateFlow.DomainHasOpenApplicationsException}
 * @error {@link DomainCreateFlow.MustHaveSignedMarksInCurrentPhaseException}
 * @error {@link DomainCreateFlow.NoGeneralRegistrationsInCurrentPhaseException}
 * @error {@link DomainCreateFlow.SignedMarksOnlyDuringSunriseException}
 * @error {@link DomainFlowTmchUtils.NoMarksFoundMatchingDomainException}
 * @error {@link DomainFlowTmchUtils.FoundMarkNotYetValidException}
 * @error {@link DomainFlowTmchUtils.FoundMarkExpiredException}
 * @error {@link DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link DomainFlowUtils.AcceptedTooLongAgoException}
 * @error {@link DomainFlowUtils.BadDomainNameCharacterException}
 * @error {@link DomainFlowUtils.BadDomainNamePartsCountException}
 * @error {@link DomainFlowUtils.DomainNameExistsAsTldException}
 * @error {@link DomainFlowUtils.BadPeriodUnitException}
 * @error {@link DomainFlowUtils.ClaimsPeriodEndedException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.CurrencyValueScaleException}
 * @error {@link DomainFlowUtils.DashesInThirdAndFourthException}
 * @error {@link DomainFlowUtils.DomainLabelTooLongException}
 * @error {@link DomainFlowUtils.DomainNotAllowedForTldWithCreateRestrictionException}
 * @error {@link DomainFlowUtils.DomainReservedException}
 * @error {@link DomainFlowUtils.DuplicateContactForRoleException}
 * @error {@link DomainFlowUtils.EmptyDomainNamePartException}
 * @error {@link DomainFlowUtils.ExceedsMaxRegistrationYearsException}
 * @error {@link DomainFlowUtils.ExpiredClaimException}
 * @error {@link DomainFlowUtils.FeeDescriptionMultipleMatchesException}
 * @error {@link DomainFlowUtils.FeeDescriptionParseException}
 * @error {@link DomainFlowUtils.FeesMismatchException}
 * @error {@link DomainFlowUtils.FeesRequiredDuringEarlyAccessProgramException}
 * @error {@link DomainFlowUtils.FeesRequiredForPremiumNameException}
 * @error {@link DomainFlowUtils.InvalidIdnDomainLabelException}
 * @error {@link DomainFlowUtils.InvalidLrpTokenException}
 * @error {@link DomainFlowUtils.InvalidPunycodeException}
 * @error {@link DomainFlowUtils.InvalidTcnIdChecksumException}
 * @error {@link DomainFlowUtils.InvalidTrademarkValidatorException}
 * @error {@link DomainFlowUtils.LeadingDashException}
 * @error {@link DomainFlowUtils.LinkedResourcesDoNotExistException}
 * @error {@link DomainFlowUtils.LinkedResourceInPendingDeleteProhibitsOperationException}
 * @error {@link DomainFlowUtils.MalformedTcnIdException}
 * @error {@link DomainFlowUtils.MaxSigLifeNotSupportedException}
 * @error {@link DomainFlowUtils.MissingAdminContactException}
 * @error {@link DomainFlowUtils.MissingClaimsNoticeException}
 * @error {@link DomainFlowUtils.MissingContactTypeException}
 * @error {@link DomainFlowUtils.MissingRegistrantException}
 * @error {@link DomainFlowUtils.MissingTechnicalContactException}
 * @error {@link DomainFlowUtils.NameserversNotAllowedForDomainException}
 * @error {@link DomainFlowUtils.NameserversNotAllowedForTldException}
 * @error {@link DomainFlowUtils.NameserversNotSpecifiedForNameserverRestrictedDomainException}
 * @error {@link DomainFlowUtils.NameserversNotSpecifiedForTldWithNameserverWhitelistException}
 * @error {@link DomainFlowUtils.PremiumNameBlockedException}
 * @error {@link DomainFlowUtils.RegistrantNotAllowedException}
 * @error {@link DomainFlowUtils.RegistrarMustBeActiveToCreateDomainsException}
 * @error {@link DomainFlowUtils.TldDoesNotExistException}
 * @error {@link DomainFlowUtils.TooManyDsRecordsException}
 * @error {@link DomainFlowUtils.TooManyNameserversException}
 * @error {@link DomainFlowUtils.TrailingDashException}
 * @error {@link DomainFlowUtils.UnexpectedClaimsNoticeException}
 * @error {@link DomainFlowUtils.UnsupportedFeeAttributeException}
 * @error {@link DomainFlowUtils.UnsupportedMarkTypeException}
 */
@ReportingSpec(ActivityReportField.DOMAIN_CREATE)
public class DomainCreateFlow implements TransactionalFlow {

  /**
   * States when the TLD is in sunrise.
   *
   * <p>Note that a TLD in SUNRUSH means sunrise is in effect, but not necessarily that the "create"
   * command is a "sunrise create". It might be a landrush create. We must make sure there's a
   * signed mark to know if the create is "sunrise" or "landrush" for verification purposes.
   *
   * <p>Note also that SUNRISE (start-date sunrise) and LANDRUSH can't "naturally" succeed in this
   * flow. They can only succeed if sent as a superuser or anchor tenant.
   */
  private static final ImmutableSet<TldState> SUNRISE_STATES =
      Sets.immutableEnumSet(SUNRISE, SUNRUSH, START_DATE_SUNRISE);

  /** Anchor tenant creates should always be for 2 years, since they get 2 years free. */
  private static final int ANCHOR_TENANT_CREATE_VALID_YEARS = 2;

  @Inject ExtensionManager extensionManager;
  @Inject EppInput eppInput;
  @Inject AuthInfo authInfo;
  @Inject ResourceCommand resourceCommand;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject @Superuser boolean isSuperuser;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject EppResponse.Builder responseBuilder;
  @Inject AllocationTokenFlowUtils allocationTokenFlowUtils;
  @Inject DomainCreateFlowCustomLogic flowCustomLogic;
  @Inject DomainFlowTmchUtils tmchUtils;
  @Inject DomainPricingLogic pricingLogic;
  @Inject DnsQueue dnsQueue;
  @Inject DomainCreateFlow() {}

  @Override
  public final EppResponse run() throws EppException {
    extensionManager.register(
        FeeCreateCommandExtension.class,
        SecDnsCreateExtension.class,
        MetadataExtension.class,
        LaunchCreateExtension.class,
        AllocationTokenExtension.class);
    flowCustomLogic.beforeValidation();
    extensionManager.validate();
    validateClientIsLoggedIn(clientId);
    verifyRegistrarIsActive(clientId);
    DateTime now = ofy().getTransactionTime();
    DomainCommand.Create command = cloneAndLinkReferences((Create) resourceCommand, now);
    Period period = command.getPeriod();
    verifyUnitIsYears(period);
    int years = period.getValue();
    validateRegistrationPeriod(years);
    verifyResourceDoesNotExist(DomainResource.class, targetId, now);
    // Validate that this is actually a legal domain name on a TLD that the registrar has access to.
    InternetDomainName domainName = validateDomainName(command.getFullyQualifiedDomainName());
    String domainLabel = domainName.parts().get(0);
    Registry registry = Registry.get(domainName.parent().toString());
    validateCreateCommandContactsAndNameservers(command, registry, domainName);
    if (registry.getDomainCreateRestricted()) {
      validateDomainAllowedOnCreateRestrictedTld(domainName);
    }
    TldState tldState = registry.getTldState(now);
    boolean isAnchorTenant = isAnchorTenant(domainName);
    verifyAnchorTenantValidPeriod(isAnchorTenant, years);
    Optional<LaunchCreateExtension> launchCreate =
        eppInput.getSingleExtension(LaunchCreateExtension.class);
    boolean hasSignedMarks =
        launchCreate.isPresent() && !launchCreate.get().getSignedMarks().isEmpty();
    boolean hasClaimsNotice = launchCreate.isPresent() && launchCreate.get().getNotice() != null;
    if (launchCreate.isPresent()) {
      verifyNoCodeMarks(launchCreate.get());
      validateLaunchCreateNotice(launchCreate.get().getNotice(), domainLabel, isSuperuser, now);
    }
    boolean isSunriseCreate = hasSignedMarks && SUNRISE_STATES.contains(tldState);
    // Superusers can create reserved domains, force creations on domains that require a claims
    // notice without specifying a claims key, ignore the registry phase, and override blocks on
    // registering premium domains.
    if (!isSuperuser) {
      checkAllowedAccessToTld(clientId, registry.getTldStr());
      if (launchCreate.isPresent()) {
        verifyLaunchPhaseMatchesRegistryPhase(registry, launchCreate.get(), now);
      }
      if (!isAnchorTenant) {
        verifyNotReserved(domainName, isSunriseCreate);
      }
      if (hasClaimsNotice) {
        verifyClaimsPeriodNotEnded(registry, now);
      }
      if (now.isBefore(registry.getClaimsPeriodEnd())) {
        verifyClaimsNoticeIfAndOnlyIfNeeded(domainName, hasSignedMarks, hasClaimsNotice);
      }
      verifyPremiumNameIsNotBlocked(targetId, now, clientId);
      verifyNoOpenApplications(now);
      verifyIsGaOrIsSpecialCase(tldState, isAnchorTenant, hasSignedMarks);
      verifySignedMarkOnlyInSunrise(hasSignedMarks, tldState);
    }
    String signedMarkId = null;
    if (hasSignedMarks) {
      // If a signed mark was provided, then it must match the desired domain label. Get the mark
      // at this point so that we can verify it before the "after validation" extension point.
      signedMarkId =
          tmchUtils
              .verifySignedMarks(launchCreate.get().getSignedMarks(), domainLabel, now)
              .getId();
    }
    Optional<AllocationToken> allocationToken =
        verifyAllocationTokenIfPresent(command, registry, clientId, now);
    flowCustomLogic.afterValidation(
        DomainCreateFlowCustomLogic.AfterValidationParameters.newBuilder()
            .setDomainName(domainName)
            .setYears(years)
            .setSignedMarkId(Optional.ofNullable(signedMarkId))
            .build());
    Optional<FeeCreateCommandExtension> feeCreate =
        eppInput.getSingleExtension(FeeCreateCommandExtension.class);
    FeesAndCredits feesAndCredits = pricingLogic.getCreatePrice(registry, targetId, now, years);
    validateFeeChallenge(targetId, registry.getTldStr(), clientId, now, feeCreate, feesAndCredits);
    Optional<SecDnsCreateExtension> secDnsCreate =
        validateSecDnsExtension(eppInput.getSingleExtension(SecDnsCreateExtension.class));
    String repoId = createDomainRepoId(ObjectifyService.allocateId(), registry.getTldStr());
    DateTime registrationExpirationTime = leapSafeAddYears(now, years);
    HistoryEntry historyEntry = buildHistoryEntry(
        repoId, registry, now, period, registry.getAddGracePeriodLength());
    // Bill for the create.
    BillingEvent.OneTime createBillingEvent =
        createOneTimeBillingEvent(
            registry, isAnchorTenant, isSunriseCreate, years, feesAndCredits, historyEntry, now);
    // Create a new autorenew billing event and poll message starting at the expiration time.
    BillingEvent.Recurring autorenewBillingEvent =
        createAutorenewBillingEvent(historyEntry, registrationExpirationTime);
    PollMessage.Autorenew autorenewPollMessage =
        createAutorenewPollMessage(historyEntry, registrationExpirationTime);
    ImmutableSet.Builder<ImmutableObject> entitiesToSave = new ImmutableSet.Builder<>();
    entitiesToSave.add(
        historyEntry,
        createBillingEvent,
        autorenewBillingEvent,
        autorenewPollMessage);
    // Bill for EAP cost, if any.
    if (!feesAndCredits.getEapCost().isZero()) {
      entitiesToSave.add(createEapBillingEvent(feesAndCredits, createBillingEvent));
    }
    DomainResource newDomain =
        new DomainResource.Builder()
            .setCreationClientId(clientId)
            .setPersistedCurrentSponsorClientId(clientId)
            .setRepoId(repoId)
            .setIdnTableName(validateDomainNameWithIdnTables(domainName))
            .setRegistrationExpirationTime(registrationExpirationTime)
            .setAutorenewBillingEvent(Key.create(autorenewBillingEvent))
            .setAutorenewPollMessage(Key.create(autorenewPollMessage))
            .setLaunchNotice(hasClaimsNotice ? launchCreate.get().getNotice() : null)
            .setSmdId(signedMarkId)
            .setDsData(secDnsCreate.isPresent() ? secDnsCreate.get().getDsData() : null)
            .setRegistrant(command.getRegistrant())
            .setAuthInfo(command.getAuthInfo())
            .setFullyQualifiedDomainName(targetId)
            .setNameservers(command.getNameservers())
            .setStatusValues(
                registry.getDomainCreateRestricted()
                    ? ImmutableSet.of(SERVER_UPDATE_PROHIBITED, SERVER_TRANSFER_PROHIBITED)
                    : ImmutableSet.of())
            .setContacts(command.getContacts())
            .addGracePeriod(GracePeriod.forBillingEvent(GracePeriodStatus.ADD, createBillingEvent))
            .build();
    entitiesToSave.add(
        newDomain,
        ForeignKeyIndex.create(newDomain, newDomain.getDeletionTime()),
        EppResourceIndex.create(Key.create(newDomain)));

    allocationToken.ifPresent(
        t -> entitiesToSave.add(allocationTokenFlowUtils.redeemToken(t, Key.create(historyEntry))));
    // Anchor tenant registrations override LRP, and landrush applications can skip it.
    // If a token is passed in outside of an LRP phase, it is simply ignored (i.e. never redeemed).
    if (isLrpCreate(registry, isAnchorTenant, now)) {
      entitiesToSave.add(
          prepareMarkedLrpTokenEntity(authInfo.getPw().getValue(), domainName, historyEntry));
    }
    enqueueTasks(newDomain, hasSignedMarks, hasClaimsNotice);

    EntityChanges entityChanges =
        flowCustomLogic.beforeSave(
            DomainCreateFlowCustomLogic.BeforeSaveParameters.newBuilder()
                .setNewDomain(newDomain)
                .setHistoryEntry(historyEntry)
                .setEntityChanges(
                    EntityChanges.newBuilder().setSaves(entitiesToSave.build()).build())
                .setYears(years)
                .build());
    persistEntityChanges(entityChanges);

    BeforeResponseReturnData responseData =
        flowCustomLogic.beforeResponse(
            BeforeResponseParameters.newBuilder()
                .setResData(DomainCreateData.create(targetId, now, registrationExpirationTime))
                .setResponseExtensions(createResponseExtensions(feeCreate, feesAndCredits))
                .build());
    return responseBuilder
        .setResData(responseData.resData())
        .setExtensions(responseData.responseExtensions())
        .build();
  }

  private boolean isAnchorTenant(InternetDomainName domainName) {
    Optional<MetadataExtension> metadataExtension =
        eppInput.getSingleExtension(MetadataExtension.class);
    return matchesAnchorTenantReservation(domainName, authInfo.getPw().getValue())
        || (metadataExtension.isPresent() && metadataExtension.get().getIsAnchorTenant());
  }

  /**
   * Verifies that signed marks are only sent during sunrise.
   *
   * <p>A trademarked domain name requires either a signed mark or a claims notice. We then need to
   * send out a LORDN message - either a "sunrise" LORDN if we have a signed mark, or a "claims"
   * LORDN if we have a claims notice.
   *
   * <p>This verification prevents us from either sending out a "sunrise" LORDN out of sunrise, or
   * not sending out any LORDN, for a trademarked domain with a signed mark in GA.
   */
  static void verifySignedMarkOnlyInSunrise(boolean hasSignedMarks, TldState tldState)
      throws EppException {
    if (hasSignedMarks && !SUNRISE_STATES.contains(tldState)) {
      throw new SignedMarksOnlyDuringSunriseException();
    }
  }

  /**
   * Verifies anchor tenant creates are only done for {@value ANCHOR_TENANT_CREATE_VALID_YEARS} year
   * periods, as anchor tenants get exactly that many years of free registration.
   */
  static void verifyAnchorTenantValidPeriod(boolean isAnchorTenant, int registrationYears)
      throws EppException {
    if (isAnchorTenant && registrationYears != ANCHOR_TENANT_CREATE_VALID_YEARS) {
      throw new AnchorTenantCreatePeriodException(registrationYears);
    }
  }

  /** Prohibit creating a domain if there is an open application for the same name. */
  private void verifyNoOpenApplications(DateTime now) throws DomainHasOpenApplicationsException {
    for (DomainApplication application : loadActiveApplicationsByDomainName(targetId, now)) {
      if (!application.getApplicationStatus().isFinalStatus()) {
        throw new DomainHasOpenApplicationsException();
      }
    }
  }

  /**
   * Prohibit registrations unless QLP, General Availability or Start Date Sunrise.
   *
   * <p>During Start-Date Sunrise, we need a signed mark for registrations.
   *
   * <p>Note that "superuser" status isn't tested here - this should only be called for
   * non-superusers.
   */
  private void verifyIsGaOrIsSpecialCase(
      TldState tldState, boolean isAnchorTenant, boolean hasSignedMarks)
      throws NoGeneralRegistrationsInCurrentPhaseException,
          MustHaveSignedMarksInCurrentPhaseException {
    // Anchor Tenant overrides any other consideration to allow registration.
    if (isAnchorTenant) {
      return;
    }

    // We allow general registration during GA.
    if (GENERAL_AVAILABILITY.equals(tldState)) {
      return;
    }

    // During START_DATE_SUNRISE, only allow registration with a signed marks.
    if (START_DATE_SUNRISE.equals(tldState)) {
      if (!hasSignedMarks) {
        throw new MustHaveSignedMarksInCurrentPhaseException();
      }
      return;
    }

    // All other phases do not allow registration
    throw new NoGeneralRegistrationsInCurrentPhaseException();
  }

  /** Verifies and returns the allocation token if one is specified, otherwise does nothing. */
  private Optional<AllocationToken> verifyAllocationTokenIfPresent(
      DomainCommand.Create command, Registry registry, String clientId, DateTime now)
      throws EppException {
    Optional<AllocationTokenExtension> extension =
        eppInput.getSingleExtension(AllocationTokenExtension.class);
    return Optional.ofNullable(
        extension.isPresent()
            ? allocationTokenFlowUtils.verifyToken(
                command, extension.get().getAllocationToken(), registry, clientId, now)
            : null);
  }

  private HistoryEntry buildHistoryEntry(
      String repoId, Registry registry, DateTime now, Period period, Duration addGracePeriod) {
    // We ignore prober transactions
    if (registry.getTldType() == TldType.REAL) {
      historyBuilder
          .setDomainTransactionRecords(
              ImmutableSet.of(
                  DomainTransactionRecord.create(
                      registry.getTldStr(),
                      now.plus(addGracePeriod),
                      TransactionReportField.netAddsFieldFromYears(period.getValue()),
                      1)));
    }
    return historyBuilder
        .setType(HistoryEntry.Type.DOMAIN_CREATE)
        .setPeriod(period)
        .setModificationTime(now)
        .setParent(Key.create(DomainResource.class, repoId))
        .build();
  }

  private OneTime createOneTimeBillingEvent(
      Registry registry,
      boolean isAnchorTenant,
      boolean isSunriseCreate,
      int years,
      FeesAndCredits feesAndCredits,
      HistoryEntry historyEntry,
      DateTime now) {
    ImmutableSet.Builder<Flag> flagsBuilder = new ImmutableSet.Builder<>();
    // Sunrise and anchor tenancy are orthogonal tags and thus both can be present together.
    if (isSunriseCreate) {
      flagsBuilder.add(Flag.SUNRISE);
    }
    if (isAnchorTenant) {
      flagsBuilder.add(Flag.ANCHOR_TENANT);
    }
    return new BillingEvent.OneTime.Builder()
        .setReason(Reason.CREATE)
        .setTargetId(targetId)
        .setClientId(clientId)
        .setPeriodYears(years)
        .setCost(feesAndCredits.getCreateCost())
        .setEventTime(now)
        .setBillingTime(
            now.plus(
                isAnchorTenant
                    ? registry.getAnchorTenantAddGracePeriodLength()
                    : registry.getAddGracePeriodLength()))
        .setFlags(flagsBuilder.build())
        .setParent(historyEntry)
        .build();
  }

  private Recurring createAutorenewBillingEvent(
      HistoryEntry historyEntry, DateTime registrationExpirationTime) {
    return new BillingEvent.Recurring.Builder()
        .setReason(Reason.RENEW)
        .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
        .setTargetId(targetId)
        .setClientId(clientId)
        .setEventTime(registrationExpirationTime)
        .setRecurrenceEndTime(END_OF_TIME)
        .setParent(historyEntry)
        .build();
  }

  private Autorenew createAutorenewPollMessage(
      HistoryEntry historyEntry, DateTime registrationExpirationTime) {
    return new PollMessage.Autorenew.Builder()
        .setTargetId(targetId)
        .setClientId(clientId)
        .setEventTime(registrationExpirationTime)
        .setMsg("Domain was auto-renewed.")
        .setParent(historyEntry)
        .build();
  }

  private static OneTime createEapBillingEvent(
      FeesAndCredits feesAndCredits, BillingEvent.OneTime createBillingEvent) {
    return new BillingEvent.OneTime.Builder()
        .setReason(Reason.FEE_EARLY_ACCESS)
        .setTargetId(createBillingEvent.getTargetId())
        .setClientId(createBillingEvent.getClientId())
        .setPeriodYears(1)
        .setCost(feesAndCredits.getEapCost())
        .setEventTime(createBillingEvent.getEventTime())
        .setBillingTime(createBillingEvent.getBillingTime())
        .setFlags(createBillingEvent.getFlags())
        .setParent(createBillingEvent.getParentKey())
        .build();
  }

  private boolean isLrpCreate(Registry registry, boolean isAnchorTenant, DateTime now) {
    return registry.getLrpPeriod().contains(now) && !isAnchorTenant;
  }

  private void enqueueTasks(
      DomainResource newDomain, boolean hasSignedMarks, boolean hasClaimsNotice) {
    if (newDomain.shouldPublishToDns()) {
      dnsQueue.addDomainRefreshTask(newDomain.getFullyQualifiedDomainName());
    }
    if (hasClaimsNotice || hasSignedMarks) {
      LordnTask.enqueueDomainResourceTask(newDomain);
    }
  }

  private static ImmutableList<FeeTransformResponseExtension> createResponseExtensions(
      Optional<FeeCreateCommandExtension> feeCreate, FeesAndCredits feesAndCredits) {
    return feeCreate.isPresent()
        ? ImmutableList.of(createFeeCreateResponse(feeCreate.get(), feesAndCredits))
        : ImmutableList.of();
  }

  /** Signed marks are only allowed during sunrise. */
  static class SignedMarksOnlyDuringSunriseException extends CommandUseErrorException {
    public SignedMarksOnlyDuringSunriseException() {
      super("Signed marks are only allowed during sunrise");
    }
  }

  /** There is an open application for this domain. */
  static class DomainHasOpenApplicationsException extends StatusProhibitsOperationException {
    public DomainHasOpenApplicationsException() {
      super("There is an open application for this domain");
    }
  }

  /** The current registry phase does not allow for general registrations. */
  static class NoGeneralRegistrationsInCurrentPhaseException extends CommandUseErrorException {
    public NoGeneralRegistrationsInCurrentPhaseException() {
      super("The current registry phase does not allow for general registrations");
    }
  }

  /** The current registry phase allows registrations only with signed marks. */
  static class MustHaveSignedMarksInCurrentPhaseException extends CommandUseErrorException {
    public MustHaveSignedMarksInCurrentPhaseException() {
      super("The current registry phase requires a signed mark for registrations");
    }
  }

  /** Anchor tenant domain create is for the wrong number of years. */
  static class AnchorTenantCreatePeriodException extends ParameterValuePolicyErrorException {
    public AnchorTenantCreatePeriodException(int invalidYears) {
      super(
          String.format(
              "Anchor tenant domain creates must be for a period of %s years, got %s instead.",
              ANCHOR_TENANT_CREATE_VALID_YEARS, invalidYears));
    }
  }
}
