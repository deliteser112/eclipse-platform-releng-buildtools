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

package com.google.domain.registry.flows.domain;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.domain.registry.flows.domain.DomainFlowUtils.validateFeeChallenge;
import static com.google.domain.registry.model.index.DomainApplicationIndex.loadActiveApplicationsByDomainName;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.EppException.CommandUseErrorException;
import com.google.domain.registry.flows.EppException.StatusProhibitsOperationException;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.domain.DomainResource.Builder;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.domain.fee.FeeCreateExtension;
import com.google.domain.registry.model.domain.launch.LaunchCreateExtension;
import com.google.domain.registry.model.domain.rgp.GracePeriodStatus;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.tmch.LordnTask;

import java.util.Set;

/**
 * An EPP flow that creates a new domain resource.
 *
 * @error {@link com.google.domain.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link com.google.domain.registry.flows.LoggedInFlow.UndeclaredServiceExtensionException}
 * @error {@link com.google.domain.registry.flows.ResourceCreateFlow.ResourceAlreadyExistsException}
 * @error {@link com.google.domain.registry.flows.ResourceCreateOrMutateFlow.OnlyToolCanPassMetadataException}
 * @error {@link com.google.domain.registry.flows.ResourceFlow.BadCommandForRegistryPhaseException}
 * @error {@link com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link BaseDomainCreateFlow.AcceptedTooLongAgoException}
 * @error {@link BaseDomainCreateFlow.ClaimsPeriodEndedException}
 * @error {@link BaseDomainCreateFlow.ExpiredClaimException}
 * @error {@link BaseDomainCreateFlow.InvalidTcnIdChecksumException}
 * @error {@link BaseDomainCreateFlow.InvalidTrademarkValidatorException}
 * @error {@link BaseDomainCreateFlow.MalformedTcnIdException}
 * @error {@link BaseDomainCreateFlow.MaxSigLifeNotSupportedException}
 * @error {@link BaseDomainCreateFlow.MissingClaimsNoticeException}
 * @error {@link BaseDomainCreateFlow.UnexpectedClaimsNoticeException}
 * @error {@link BaseDomainCreateFlow.UnsupportedMarkTypeException}
 * @error {@link DomainCreateFlow.SignedMarksNotAcceptedInCurrentPhaseException}
 * @error {@link DomainFlowUtils.BadDomainNameCharacterException}
 * @error {@link DomainFlowUtils.BadDomainNamePartsCountException}
 * @error {@link DomainFlowUtils.BadPeriodUnitException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.CurrencyValueScaleException}
 * @error {@link DomainFlowUtils.DashesInThirdAndFourthException}
 * @error {@link DomainFlowUtils.DomainLabelTooLongException}
 * @error {@link DomainFlowUtils.DomainReservedException}
 * @error {@link DomainFlowUtils.DuplicateContactForRoleException}
 * @error {@link DomainFlowUtils.EmptyDomainNamePartException}
 * @error {@link DomainFlowUtils.FeesMismatchException}
 * @error {@link DomainFlowUtils.FeesRequiredForPremiumNameException}
 * @error {@link DomainFlowUtils.InvalidIdnDomainLabelException}
 * @error {@link DomainFlowUtils.InvalidPunycodeException}
 * @error {@link DomainFlowUtils.LeadingDashException}
 * @error {@link DomainFlowUtils.LinkedResourceDoesNotExistException}
 * @error {@link DomainFlowUtils.LinkedResourceInPendingDeleteProhibitsOperationException}
 * @error {@link DomainFlowUtils.MissingAdminContactException}
 * @error {@link DomainFlowUtils.MissingContactTypeException}
 * @error {@link DomainFlowUtils.MissingRegistrantException}
 * @error {@link DomainFlowUtils.MissingTechnicalContactException}
 * @error {@link DomainFlowUtils.NameserverNotAllowedException}
 * @error {@link DomainFlowUtils.PremiumNameBlockedException}
 * @error {@link DomainFlowUtils.RegistrantNotAllowedException}
 * @error {@link DomainFlowUtils.TldDoesNotExistException}
 * @error {@link DomainFlowUtils.TooManyDsRecordsException}
 * @error {@link DomainFlowUtils.TooManyNameserversException}
 * @error {@link DomainFlowUtils.TrailingDashException}
 * @error {@link DomainFlowUtils.UnsupportedFeeAttributeException}
 * @error {@link DomainCreateFlow.DomainHasOpenApplicationsException}
 * @error {@link DomainCreateFlow.NoGeneralRegistrationsInCurrentPhaseException}
 */

public class DomainCreateFlow extends DomainCreateOrAllocateFlow {

  private static final Set<TldState> QLP_SMD_ALLOWED_STATES =
      Sets.immutableEnumSet(TldState.SUNRISE, TldState.SUNRUSH);

  private boolean isAnchorTenant() {
    return isAnchorTenantViaReservation || isAnchorTenantViaExtension;
  }

  @Override
  protected final void verifyDomainCreateIsAllowed() throws EppException {
    String tld = getTld();
    validateFeeChallenge(targetId, tld, feeCreate, createCost);
    if (!superuser) {
      // Prohibit creating a domain if there is an open application for the same name.
      for (DomainApplication application : loadActiveApplicationsByDomainName(targetId, now)) {
        if (!application.getApplicationStatus().isFinalStatus()) {
          throw new DomainHasOpenApplicationsException();
        }
      }
      // Prohibit registrations for non-qlp and non-superuser outside of GA.
      if (!isAnchorTenant()
          && Registry.get(tld).getTldState(now) != TldState.GENERAL_AVAILABILITY) {
        throw new NoGeneralRegistrationsInCurrentPhaseException();
      }
    }
  }

  @Override
  protected final void initDomainCreateOrAllocateFlow() {
    registerExtensions(FeeCreateExtension.class, LaunchCreateExtension.class);
  }

  @Override
  protected final void validateDomainLaunchCreateExtension() throws EppException {
    // We can assume launchCreate is not null here.
    // Only QLP domains can have a signed mark on a domain create, and only in sunrise or sunrush.
    if (hasSignedMarks) {
      if (isAnchorTenant() && QLP_SMD_ALLOWED_STATES.contains(
          Registry.get(getTld()).getTldState(now))) {
        return;
      }
      throw new SignedMarksNotAcceptedInCurrentPhaseException();
    }
  }

  @Override
  protected final void setDomainCreateOrAllocateProperties(Builder builder) throws EppException {
    Registry registry = Registry.get(getTld());
    // Bill for the create.
    BillingEvent.OneTime createEvent = new BillingEvent.OneTime.Builder()
        .setReason(Reason.CREATE)
        .setTargetId(targetId)
        .setClientId(getClientId())
        .setPeriodYears(command.getPeriod().getValue())
        .setCost(checkNotNull(createCost))
        .setEventTime(now)
        .setBillingTime(now.plus(isAnchorTenant()
            ? registry.getAnchorTenantAddGracePeriodLength()
            : registry.getAddGracePeriodLength()))
        .setFlags(isAnchorTenant() ? ImmutableSet.of(BillingEvent.Flag.ANCHOR_TENANT) : null)
        .setParent(historyEntry)
        .build();
    ofy().save().entity(createEvent);
    builder.addGracePeriod(GracePeriod.forBillingEvent(GracePeriodStatus.ADD, createEvent));
    if (launchCreate != null && (launchCreate.getNotice() != null || hasSignedMarks)) {
      builder
          .setLaunchNotice(launchCreate.getNotice())
          .setSmdId(signedMark == null ? null : signedMark.getId());
    }
  }

  @Override
  protected void enqueueLordnTaskIfNeeded() {
    if (launchCreate != null && (launchCreate.getNotice() != null || hasSignedMarks)) {
      LordnTask.enqueueDomainResourceTask(newResource);
    }
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_CREATE;
  }

  /** There is an open application for this domain. */
  static class DomainHasOpenApplicationsException extends StatusProhibitsOperationException {
    public DomainHasOpenApplicationsException() {
      super("There is an open application for this domain");
    }
  }

  /** Signed marks are not accepted in the current registry phase. */
  static class SignedMarksNotAcceptedInCurrentPhaseException extends CommandUseErrorException {
    public SignedMarksNotAcceptedInCurrentPhaseException() {
      super("Signed marks are not accepted in the current registry phase");
    }
  }

  /** The current registry phase does not allow for general registrations. */
  static class NoGeneralRegistrationsInCurrentPhaseException extends CommandUseErrorException {
    public NoGeneralRegistrationsInCurrentPhaseException() {
      super("The current registry phase does not allow for general registrations");
    }
  }
}
