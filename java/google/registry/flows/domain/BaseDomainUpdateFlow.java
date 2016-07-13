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

import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.union;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.cloneAndLinkReferences;
import static google.registry.flows.domain.DomainFlowUtils.validateContactsHaveTypes;
import static google.registry.flows.domain.DomainFlowUtils.validateDsData;
import static google.registry.flows.domain.DomainFlowUtils.validateNameserversAllowedOnTld;
import static google.registry.flows.domain.DomainFlowUtils.validateNameserversCountForTld;
import static google.registry.flows.domain.DomainFlowUtils.validateNoDuplicateContacts;
import static google.registry.flows.domain.DomainFlowUtils.validateRegistrantAllowedOnTld;
import static google.registry.flows.domain.DomainFlowUtils.validateRequiredContactsPresent;
import static google.registry.flows.domain.DomainFlowUtils.verifyNotInPendingDelete;

import com.google.common.collect.ImmutableSet;

import google.registry.flows.EppException;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.flows.EppException.RequiredParameterMissingException;
import google.registry.flows.EppException.UnimplementedOptionException;
import google.registry.flows.ResourceUpdateFlow;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.DomainBase.Builder;
import google.registry.model.domain.DomainCommand.Update;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.domain.secdns.SecDnsUpdateExtension;
import google.registry.model.domain.secdns.SecDnsUpdateExtension.Add;
import google.registry.model.domain.secdns.SecDnsUpdateExtension.Remove;

import java.util.Set;

/**
 * An EPP flow that updates a domain application or resource.
 *
 * @param <R> the resource type being created
 * @param <B> a builder for the resource
 */
public abstract class BaseDomainUpdateFlow<R extends DomainBase, B extends Builder<R, B>>
    extends ResourceUpdateFlow<R, B, Update> {

  @Override
  public final void initResourceCreateOrMutateFlow() throws EppException {
    command = cloneAndLinkReferences(command, now);
    initDomainUpdateFlow();
  }

  @SuppressWarnings("unused")
  protected void initDomainUpdateFlow() throws EppException {}

  @Override
  public final B setUpdateProperties(B builder) throws EppException {
    // Handle the secDNS extension.
    SecDnsUpdateExtension secDnsUpdate = eppInput.getSingleExtension(SecDnsUpdateExtension.class);
    if (secDnsUpdate != null) {
      // We don't support 'urgent' because we do everything as fast as we can anyways.
      if (Boolean.TRUE.equals(secDnsUpdate.getUrgent())) {  // We allow both false and null.
        throw new UrgentAttributeNotSupportedException();
      }
      // There must be at least one of add/rem/chg, and chg isn't actually supported.
      if (secDnsUpdate.getAdd() == null && secDnsUpdate.getRemove() == null) {
        // The only thing you can change is maxSigLife, and we don't support that at all.
        throw (secDnsUpdate.getChange() == null)
            ? new EmptySecDnsUpdateException()
            : new MaxSigLifeChangeNotSupportedException();
      }
      Set<DelegationSignerData> newDsData = existingResource.getDsData();
      // RFC 5910 specifies that removes are processed before adds.
      Remove remove = secDnsUpdate.getRemove();
      if (remove != null) {
        if (Boolean.FALSE.equals(remove.getAll())) {  // Explicit all=false is meaningless.
          throw new SecDnsAllUsageException();
        }
        newDsData = (remove.getAll() == null)
            ? difference(existingResource.getDsData(), remove.getDsData())
            : ImmutableSet.<DelegationSignerData>of();
      }
      Add add = secDnsUpdate.getAdd();
      if (add != null) {
        newDsData = union(newDsData, add.getDsData());
      }
      builder.setDsData(ImmutableSet.copyOf(newDsData));
    }
    return setDomainUpdateProperties(builder);
  }

  /** Subclasses can override this to do set more specific properties. */
  protected B setDomainUpdateProperties(B builder) {
    return builder;
  }

  @Override
  protected final void verifyUpdateIsAllowed() throws EppException {
    checkAllowedAccessToTld(getAllowedTlds(), existingResource.getTld());
    verifyDomainUpdateIsAllowed();
    verifyNotInPendingDelete(
        command.getInnerAdd().getContacts(),
        command.getInnerChange().getRegistrant(),
        command.getInnerAdd().getNameservers());
    validateContactsHaveTypes(command.getInnerAdd().getContacts());
    validateContactsHaveTypes(command.getInnerRemove().getContacts());
    validateRegistrantAllowedOnTld(
        existingResource.getTld(), command.getInnerChange().getRegistrantContactId());
    validateNameserversAllowedOnTld(
        existingResource.getTld(), command.getInnerAdd().getNameserverFullyQualifiedHostNames());
  }

  /** Subclasses can override this to do more specific verification. */
  @SuppressWarnings("unused")
  protected void verifyDomainUpdateIsAllowed() throws EppException {}

  @Override
  protected final void verifyNewUpdatedStateIsAllowed() throws EppException {
    validateNoDuplicateContacts(newResource.getContacts());
    validateRequiredContactsPresent(newResource.getRegistrant(), newResource.getContacts());
    validateDsData(newResource.getDsData());
    validateNameserversCountForTld(newResource.getTld(), newResource.getNameservers().size());
  }

  /** The secDNS:all element must have value 'true' if present. */
  static class SecDnsAllUsageException extends ParameterValuePolicyErrorException {
    public SecDnsAllUsageException() {
      super("The secDNS:all element must have value 'true' if present");
    }
  }

  /** At least one of 'add' or 'rem' is required on a secDNS update. */
  static class EmptySecDnsUpdateException extends RequiredParameterMissingException {
    public EmptySecDnsUpdateException() {
      super("At least one of 'add' or 'rem' is required on a secDNS update");
    }
  }

  /** The 'urgent' attribute is not supported. */
  static class UrgentAttributeNotSupportedException extends UnimplementedOptionException {
    public UrgentAttributeNotSupportedException() {
      super("The 'urgent' attribute is not supported");
    }
  }

  /** Changing 'maxSigLife' is not supported. */
  static class MaxSigLifeChangeNotSupportedException extends UnimplementedOptionException {
    public MaxSigLifeChangeNotSupportedException() {
      super("Changing 'maxSigLife' is not supported");
    }
  }
}
