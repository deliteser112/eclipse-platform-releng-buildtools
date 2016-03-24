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

package com.google.domain.registry.model.domain;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.intersection;
import static com.google.domain.registry.model.index.ForeignKeyIndex.loadAndGetReference;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.util.CollectionUtils.nullSafeImmutableCopy;
import static com.google.domain.registry.util.CollectionUtils.nullToEmptyImmutableCopy;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.eppcommon.AuthInfo;
import com.google.domain.registry.model.eppinput.ResourceCommand.AbstractSingleResourceCommand;
import com.google.domain.registry.model.eppinput.ResourceCommand.ResourceCheck;
import com.google.domain.registry.model.eppinput.ResourceCommand.ResourceCreateOrChange;
import com.google.domain.registry.model.eppinput.ResourceCommand.ResourceUpdate;
import com.google.domain.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import com.google.domain.registry.model.host.HostResource;

import com.googlecode.objectify.Ref;
import com.googlecode.objectify.Work;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

/** A collection of {@link DomainResource} commands. */
public class DomainCommand {

  /** The default validity period (if not specified) is 1 year for all operations. */
  static final Period DEFAULT_PERIOD = Period.create(1, Period.Unit.YEARS);

  /**
   * A common interface for {@link Create} and {@link Update} to support linking resources.
   *
   * @param <T> the actual type (either {@link Create} or {@link Update})
   */
  public interface CreateOrUpdate<T extends CreateOrUpdate<T>> extends SingleResourceCommand {
    /** Creates a copy of this command with hard links to hosts and contacts. */
    public T cloneAndLinkReferences(DateTime now) throws InvalidReferenceException;
  }

  /** The fields on "chgType" from {@link "http://tools.ietf.org/html/rfc5731"}. */
  @XmlTransient
  public static class DomainCreateOrChange<B extends DomainBase.Builder<?, ?>>
      extends ImmutableObject implements ResourceCreateOrChange<B> {

    /** A reference to the registrant who registered this domain. */
    ReferenceUnion<ContactResource> registrant;

    /** Authorization info (aka transfer secret) of the domain. */
    DomainAuthInfo authInfo;

    @Override
    public void applyTo(B builder) {
      if (registrant != null) {
        builder.setRegistrant(registrant);
      }
      if (authInfo != null) {
        builder.setAuthInfo(authInfo);
      }
    }
  }

  /**
   * A create command for a {@link DomainBase}, mapping "createType" from
   * {@link "http://tools.ietf.org/html/rfc5731"}.
   */
  @XmlRootElement
  @XmlType(propOrder = {
      "fullyQualifiedDomainName", "period", "nameservers", "registrant", "contacts", "authInfo" })
  public static class Create
      extends DomainCreateOrChange<DomainBase.Builder<?, ?>>
      implements CreateOrUpdate<Create> {

    /** Fully qualified domain name, which serves as a unique identifier for this domain. */
    @XmlElement(name = "name")
    String fullyQualifiedDomainName;

    /** References to hosts that are the nameservers for the domain. */
    @XmlElementWrapper(name = "ns")
    @XmlElement(name = "hostObj")
    Set<ReferenceUnion<HostResource>> nameservers;

    /** Associated contacts for the domain (other than registrant).  */
    @XmlElement(name = "contact")
    Set<DesignatedContact> contacts;

    /** The period that this domain's state was set to last for (e.g. 1-10 years). */
    Period period;

    public Period getPeriod() {
      return firstNonNull(period, DEFAULT_PERIOD);
    }

    @Override
    public String getTargetId() {
      return fullyQualifiedDomainName;
    }

    public String getFullyQualifiedDomainName() {
      return fullyQualifiedDomainName;
    }

    public ImmutableSet<ReferenceUnion<HostResource>> getNameservers() {
      return nullSafeImmutableCopy(nameservers);
    }

    public ImmutableSet<DesignatedContact> getContacts() {
      return nullSafeImmutableCopy(contacts);
    }

    public ReferenceUnion<ContactResource> getRegistrant() {
      return registrant;
    }

   @Override
    public AuthInfo getAuthInfo() {
      return authInfo;
    }

    @Override
    public void applyTo(DomainBase.Builder<?, ?> builder) {
      super.applyTo(builder);
      if (fullyQualifiedDomainName != null) {
        builder.setFullyQualifiedDomainName(fullyQualifiedDomainName);
      }
      if (nameservers != null) {
        builder.setNameservers(getNameservers());
      }
      if (contacts != null) {
        builder.setContacts(getContacts());
      }
    }

    /** Creates a copy of this {@link Create} with hard links to hosts and contacts. */
    @Override
    public Create cloneAndLinkReferences(DateTime now) throws InvalidReferenceException {
      Create clone = clone(this);
      clone.nameservers = linkHosts(clone.nameservers, now);
      clone.contacts = linkContacts(clone.contacts, now);
      clone.registrant = clone.registrant == null
          ? null : linkReference(clone.registrant, ContactResource.class, now);
      return clone;
    }
  }

  /** A delete command for a {@link DomainBase}. */
  @XmlRootElement
  public static class Delete extends AbstractSingleResourceCommand {}

  /** An info request for a {@link DomainBase}. */
  @XmlRootElement
  public static class Info extends ImmutableObject implements SingleResourceCommand {

    /** The name of the domain to look up, and an attribute specifying the host lookup type. */
    @XmlElement(name = "name")
    NameWithHosts fullyQualifiedDomainName;

    DomainAuthInfo authInfo;

    /** Enum of the possible values for the "hosts" attribute in info flows. */
    public enum HostsRequest {
      @XmlEnumValue("all")
      ALL,

      @XmlEnumValue("del")
      DELEGATED,

      @XmlEnumValue("sub")
      SUBORDINATE,

      @XmlEnumValue("none")
      NONE;

      public boolean requestDelegated() {
        return this == ALL || this == DELEGATED;
      }

      public boolean requestSubordinate() {
        return this == ALL || this == SUBORDINATE;
      }
    }

    /** Info commands use a variant syntax where the name tag has a "hosts" attribute. */
    public static class NameWithHosts extends ImmutableObject {
      @XmlAttribute
      HostsRequest hosts;

      @XmlValue
      String name;
    }

    /** Get the enum that specifies the requested hosts (applies only to info flows). */
    public HostsRequest getHostsRequest() {
      // Null "hosts" is implicitly ALL.
      return MoreObjects.firstNonNull(fullyQualifiedDomainName.hosts, HostsRequest.ALL);
    }

    @Override
    public String getTargetId() {
      return fullyQualifiedDomainName.name;
    }

    @Override
    public DomainAuthInfo getAuthInfo() {
      return authInfo;
    }
  }

  /** A check request for {@link DomainResource}. */
  @XmlRootElement
  public static class Check extends ResourceCheck {}

  /** A renew command for a {@link DomainResource}. */
  @XmlRootElement
  public static class Renew extends AbstractSingleResourceCommand {
    @XmlElement(name = "curExpDate")
    LocalDate currentExpirationDate;

    /** The period that this domain's state was set to last for. */
    Period period;

    public LocalDate getCurrentExpirationDate() {
      return currentExpirationDate;
    }

    public Period getPeriod() {
      return firstNonNull(period, DEFAULT_PERIOD);
    }
  }

  /** A transfer operation for a {@link DomainResource}. */
  @XmlRootElement
  public static class Transfer extends AbstractSingleResourceCommand {
    /** The period to extend this domain's registration upon completion of the transfer. */
    Period period;

    /** Authorization info used to validate if client has permissions to perform this operation. */
    DomainAuthInfo authInfo;

    public Period getPeriod() {
      return firstNonNull(period, DEFAULT_PERIOD);
    }

    @Override
    public DomainAuthInfo getAuthInfo() {
      return authInfo;
    }
  }

  /** An update to a {@link DomainBase}. */
  @XmlRootElement
  @XmlType(propOrder = {"targetId", "innerAdd", "innerRemove", "innerChange"})
  public static class Update
      extends ResourceUpdate<Update.AddRemove, DomainBase.Builder<?, ?>, Update.Change>
      implements CreateOrUpdate<Update> {

    @XmlElement(name = "chg")
    protected Change innerChange;

    @XmlElement(name = "add")
    protected AddRemove innerAdd;

    @XmlElement(name = "rem")
    protected AddRemove innerRemove;

    @Override
    protected Change getNullableInnerChange() {
      return innerChange;
    }

    @Override
    protected AddRemove getNullableInnerAdd() {
      return innerAdd;
    }

    @Override
    protected AddRemove getNullableInnerRemove() {
      return innerRemove;
    }

    public boolean noChangesPresent() {
      AddRemove emptyAddRemove = new AddRemove();
      return emptyAddRemove.equals(getInnerAdd())
          && emptyAddRemove.equals(getInnerRemove())
          && new Change().equals(getInnerChange());
    }

    /** The inner change type on a domain update command. */
    @XmlType(propOrder = {"nameservers", "contacts", "statusValues"})
    public static class AddRemove extends ResourceUpdate.AddRemove {
      /** References to hosts that are the nameservers for the domain. */
      @XmlElementWrapper(name = "ns")
      @XmlElement(name = "hostObj")
      Set<ReferenceUnion<HostResource>> nameservers;

      /** Associated contacts for the domain. */
      @XmlElement(name = "contact")
      Set<DesignatedContact> contacts;

      public ImmutableSet<ReferenceUnion<HostResource>> getNameservers() {
        return nullToEmptyImmutableCopy(nameservers);
      }

      public ImmutableSet<DesignatedContact> getContacts() {
        return nullToEmptyImmutableCopy(contacts);
      }

      /** Creates a copy of this {@link AddRemove} with hard links to hosts and contacts. */
      private AddRemove cloneAndLinkReferences(DateTime now) throws InvalidReferenceException {
        AddRemove clone = clone(this);
        clone.nameservers = linkHosts(clone.nameservers, now);
        clone.contacts = linkContacts(clone.contacts, now);
        return clone;
      }
    }

    /** The inner change type on a domain update command. */
    @XmlType(propOrder = {"registrant", "authInfo"})
    public static class Change extends DomainCreateOrChange<DomainBase.Builder<?, ?>> {

      public ReferenceUnion<ContactResource> getRegistrant() {
        return registrant;
      }

      /** Creates a copy of this {@link Change} with hard links to hosts and contacts. */
      Change cloneAndLinkReferences(DateTime now) throws InvalidReferenceException {
        Change clone = clone(this);
        clone.registrant = clone.registrant == null
            ? null : linkReference(clone.registrant, ContactResource.class, now);
        return clone;
      }
    }

    @Override
    public void applyTo(DomainBase.Builder<?, ?> builder) throws AddRemoveSameValueException {
      super.applyTo(builder);
      getInnerChange().applyTo(builder);
      AddRemove add = getInnerAdd();
      AddRemove remove = getInnerRemove();
      if (!intersection(add.getNameservers(), remove.getNameservers()).isEmpty()) {
        throw new AddRemoveSameValueException();
      }
      builder.addNameservers(add.getNameservers());
      builder.removeNameservers(remove.getNameservers());
      if (!intersection(add.getContacts(), remove.getContacts()).isEmpty()) {
        throw new AddRemoveSameValueException();
      }
      builder.addContacts(add.getContacts());
      builder.removeContacts(remove.getContacts());
    }

    /**
     * Creates a copy of this {@link Update} with hard links to hosts and contacts.
     * <p>
     * As a side effect, this will turn null innerAdd/innerRemove/innerChange into empty versions of
     * those classes, which is harmless because the getters do that anyways.
     */
    @Override
    public Update cloneAndLinkReferences(DateTime now) throws InvalidReferenceException {
      Update clone = clone(this);
      clone.innerAdd = clone.getInnerAdd().cloneAndLinkReferences(now);
      clone.innerRemove = clone.getInnerRemove().cloneAndLinkReferences(now);
      clone.innerChange = clone.getInnerChange().cloneAndLinkReferences(now);
      return clone;
    }
  }

  private static Set<ReferenceUnion<HostResource>> linkHosts(
      Set<ReferenceUnion<HostResource>> hosts,
      DateTime now) throws InvalidReferenceException {
    if (hosts == null) {
      return null;
    }
    ImmutableSet.Builder<ReferenceUnion<HostResource>> linked = new ImmutableSet.Builder<>();
    for (ReferenceUnion<HostResource> host : hosts) {
      linked.add(linkReference(host, HostResource.class, now));
    }
    return linked.build();
  }

  private static Set<DesignatedContact> linkContacts(
      Set<DesignatedContact> contacts, DateTime now) throws InvalidReferenceException {
    if (contacts == null) {
      return null;
    }
    ImmutableSet.Builder<DesignatedContact> linkedContacts = new ImmutableSet.Builder<>();
    for (DesignatedContact contact : contacts) {
      linkedContacts.add(DesignatedContact.create(
          contact.getType(),
          linkReference(contact.getContactId(), ContactResource.class, now)));
    }
    return linkedContacts.build();
  }

  /** Turn a foreign-keyed {@link ReferenceUnion} into a linked one. */
  private static <T extends EppResource> ReferenceUnion<T> linkReference(
      final ReferenceUnion<T> reference, final Class<T> clazz, final DateTime now)
      throws InvalidReferenceException {
    if (reference.getForeignKey() == null) {
      return reference;
    }
    Ref<T> ref = ofy().doTransactionless(new Work<Ref<T>>() {
      @Override
      public Ref<T> run() {
        return loadAndGetReference(clazz, reference.getForeignKey(), now);
      }
    });
    if (ref == null) {
      throw new InvalidReferenceException(clazz, reference.getForeignKey());
    }
    return ReferenceUnion.create(ref);
  }

  /** Exception to throw when a referenced object does not exist. */
  public static class InvalidReferenceException extends Exception {
    private String foreignKey;
    private Class<?> type;

    InvalidReferenceException(Class<?> type, String foreignKey) {
      this.type = checkNotNull(type);
      this.foreignKey = foreignKey;
    }

    public String getForeignKey() {
      return foreignKey;
    }

    public Class<?> getType() {
      return type;
    }
  }
}
