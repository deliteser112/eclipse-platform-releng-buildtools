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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.union;
import static com.google.domain.registry.model.domain.DesignatedContact.Type.REGISTRANT;
import static com.google.domain.registry.util.CollectionUtils.nullToEmptyImmutableCopy;
import static com.google.domain.registry.util.CollectionUtils.nullToEmptyImmutableSortedCopy;
import static com.google.domain.registry.util.CollectionUtils.union;
import static com.google.domain.registry.util.DomainNameUtils.getTldFromDomainName;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.EppResourceUtils;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.launch.LaunchNotice;
import com.google.domain.registry.model.domain.secdns.DelegationSignerData;
import com.google.domain.registry.model.eppcommon.AuthInfo;
import com.google.domain.registry.model.host.HostResource;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Ignore;
import com.googlecode.objectify.annotation.IgnoreSave;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.OnLoad;
import com.googlecode.objectify.condition.IfNull;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlTransient;

/** Shared base class for {@link DomainResource} and {@link DomainApplication}. */
@XmlTransient
@Entity
public abstract class DomainBase extends EppResource {

  /**
   * Fully qualified domain name (puny-coded), which serves as the foreign key for this domain.
   * <p>
   * This is only unique in the sense that for any given lifetime specified as the time range from
   * (creationTime, deletionTime) there can only be one domain in the datastore with this name.
   * However, there can be many domains with the same name and non-overlapping lifetimes.
   *
   * @invariant fullyQualifiedDomainName == fullyQualifiedDomainName.toLowerCase()
   */
  @Index
  @XmlElement(name = "name")
  String fullyQualifiedDomainName;

  /** The top level domain this is under, dernormalized from {@link #fullyQualifiedDomainName}. */
  @Index
  @XmlTransient
  String tld;

  /** References to hosts that are the nameservers for the domain. */
  @XmlElementWrapper(name = "ns")
  @XmlElement(name = "hostObj")
  Set<ReferenceUnion<HostResource>> nameservers;

  /**
   * Associated contacts for the domain (other than registrant).
   * <p>
   * This field is marked with {@literal @}Ignore so that {@link DomainBase} subclasses won't
   * persist it. Instead, the data in this field and in the {@link #registrant} are both stored in
   * {@link DomainBase#allContacts} to allow for more efficient queries.
   */
  @Ignore
  @XmlElement(name = "contact")
  Set<DesignatedContact> contacts;

  /**
   * The union of the contacts in {@link #contacts} and {@link #registrant}. This is so we can query
   * across all contacts at once. It is maintained by the builder and by {@link #unpackageContacts}.
   */
  @XmlTransient
  Set<DesignatedContact> allContacts;

  /**
   * A reference to the registrant who registered this domain.
   * <p>
   * This field is marked with {@literal @}Ignore so that {@link DomainBase} subclasses won't
   * persist it. Instead, the data in this field and in the {@link DomainBase#contacts} are
   * both stored in {@link DomainBase#allContacts} to allow for more efficient queries.
   */
  @Ignore
  ReferenceUnion<ContactResource> registrant;

  /** Authorization info (aka transfer secret) of the domain. */
  DomainAuthInfo authInfo;

  /**
   * Data used to construct DS records for this domain.
   * <p>
   * This is {@literal @}XmlTransient because it needs to be returned under the "extension" tag
   * of an info response rather than inside the "infData" tag.
   */
  @XmlTransient
  Set<DelegationSignerData> dsData;

  /**
   * The claims notice supplied when this application or domain was created, if there was one. It's
   * {@literal @}XmlTransient because it's not returned in an info response.
   */
  @IgnoreSave(IfNull.class)
  @XmlTransient
  LaunchNotice launchNotice;

  /**
   * Name of first IDN table associated with TLD that matched the characters in this domain label.
   *
   * @see com.google.domain.registry.tldconfig.idn.IdnLabelValidator#findValidIdnTableForTld
   */
  @IgnoreSave(IfNull.class)
  @XmlTransient
  String idnTableName;

  public String getFullyQualifiedDomainName() {
    return fullyQualifiedDomainName;
  }

  public ImmutableSortedSet<DelegationSignerData> getDsData() {
    return nullToEmptyImmutableSortedCopy(dsData);
  }

  public LaunchNotice getLaunchNotice() {
    return launchNotice;
  }

  public String getIdnTableName() {
    return idnTableName;
  }

  public ImmutableSet<ReferenceUnion<HostResource>> getNameservers() {
    return nullToEmptyImmutableCopy(nameservers);
  }

  /** Loads and returns all linked nameservers. */
  public ImmutableSet<HostResource> loadNameservers() {
    return EppResourceUtils.loadReferencedNameservers(getNameservers());
  }

  public ReferenceUnion<ContactResource> getRegistrant() {
    return registrant;
  }

  public ContactResource loadRegistrant() {
    return registrant.getLinked().get();
  }

  public ImmutableSet<DesignatedContact> getContacts() {
    return nullToEmptyImmutableCopy(contacts);
  }

  public AuthInfo getAuthInfo() {
    return authInfo;
  }

  /** Returns all referenced contacts from this domain or application. */
  public ImmutableSet<ReferenceUnion<ContactResource>> getReferencedContacts() {
    ImmutableSet.Builder<ReferenceUnion<ContactResource>> contactsBuilder =
        new ImmutableSet.Builder<>();
    for (DesignatedContact designated : nullToEmptyImmutableCopy(allContacts)) {
      contactsBuilder.add(designated.getContactId());
    }
    return contactsBuilder.build();
  }

  /** Loads and returns all referenced contacts from this domain or application. */
  public ImmutableSet<ContactResource> loadReferencedContacts() {
    return EppResourceUtils.loadReferencedContacts(getReferencedContacts());
  }

  public String getTld() {
    return tld;
  }

  /**
   * On load, unpackage the {@link #allContacts} field, placing the registrant into
   * {@link #registrant} field and all other contacts into {@link #contacts}.
   */
  @OnLoad
  void unpackageContacts() {
    ImmutableSet.Builder<DesignatedContact> contactsBuilder = new ImmutableSet.Builder<>();
    for (DesignatedContact contact : nullToEmptyImmutableCopy(allContacts)) {
      if (REGISTRANT.equals(contact.getType())){
        registrant = contact.getContactId();
      } else {
        contactsBuilder.add(contact);
      }
    }
    contacts = contactsBuilder.build();
  }

  /** An override of {@link EppResource#asBuilder} with tighter typing. */
  @Override
  public abstract Builder<?, ?> asBuilder();

  /** A builder for constructing {@link DomainBase}, since it is immutable. */
  public abstract static class Builder<T extends DomainBase, B extends Builder<?, ?>>
      extends EppResource.Builder<T, B> {
    protected Builder() {}

    protected Builder(T instance) {
      super(instance);
    }

    @Override
    public T build() {
      T instance = getInstance();
      checkState(
          !isNullOrEmpty(instance.fullyQualifiedDomainName), "Missing fullyQualifiedDomainName");
      instance.tld = getTldFromDomainName(instance.fullyQualifiedDomainName);
      instance.allContacts = instance.registrant == null ? instance.contacts : union(
          instance.getContacts(), DesignatedContact.create(REGISTRANT, instance.registrant));
      return super.build();
    }

    public B setFullyQualifiedDomainName(String fullyQualifiedDomainName) {
      getInstance().fullyQualifiedDomainName = fullyQualifiedDomainName;
      return thisCastToDerived();
    }

    public B setDsData(ImmutableSet<DelegationSignerData> dsData) {
      getInstance().dsData = dsData;
      return thisCastToDerived();
    }

    public B setRegistrant(ReferenceUnion<ContactResource> registrant) {
      getInstance().registrant = registrant;
      return thisCastToDerived();
    }

    public B setAuthInfo(DomainAuthInfo authInfo) {
      getInstance().authInfo = authInfo;
      return thisCastToDerived();
    }

    public B setNameservers(ImmutableSet<ReferenceUnion<HostResource>> nameservers) {
      getInstance().nameservers = nameservers;
      return thisCastToDerived();
    }

    public B addNameservers(ImmutableSet<ReferenceUnion<HostResource>> nameservers) {
      return setNameservers(
          ImmutableSet.copyOf(union(getInstance().getNameservers(), nameservers)));
    }

    public B removeNameservers(ImmutableSet<ReferenceUnion<HostResource>> nameservers) {
      return setNameservers(
          ImmutableSet.copyOf(difference(getInstance().getNameservers(), nameservers)));
    }

    public B setContacts(ImmutableSet<DesignatedContact> contacts) {
      getInstance().contacts = contacts;
      return thisCastToDerived();
    }

    public B addContacts(ImmutableSet<DesignatedContact> contacts) {
      return setContacts(ImmutableSet.copyOf(union(getInstance().getContacts(), contacts)));
    }

    public B removeContacts(ImmutableSet<DesignatedContact> contacts) {
      return setContacts(ImmutableSet.copyOf(difference(getInstance().getContacts(), contacts)));
    }

    public B setLaunchNotice(LaunchNotice launchNotice) {
      getInstance().launchNotice = launchNotice;
      return thisCastToDerived();
    }

    public B setIdnTableName(String idnTableName) {
      getInstance().idnTableName = idnTableName;
      return thisCastToDerived();
    }
  }
}
