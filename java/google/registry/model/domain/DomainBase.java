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

package google.registry.model.domain;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.union;
import static google.registry.model.domain.DesignatedContact.Type.REGISTRANT;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static google.registry.util.CollectionUtils.nullToEmptyImmutableCopy;
import static google.registry.util.CollectionUtils.nullToEmptyImmutableSortedCopy;
import static google.registry.util.CollectionUtils.union;
import static google.registry.util.DomainNameUtils.getTldFromSld;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import com.googlecode.objectify.Ref;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Ignore;
import com.googlecode.objectify.annotation.IgnoreSave;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.OnLoad;
import com.googlecode.objectify.condition.IfNull;

import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.host.HostResource;

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
   *
   * <p>This is only unique in the sense that for any given lifetime specified as the time range
   * from (creationTime, deletionTime) there can only be one domain in the datastore with this name.
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
  //TODO(b/28713909): Make this a Set<Ref<HostResource>>.
  Set<ReferenceUnion<HostResource>> nameservers;

  /**
   * Associated contacts for the domain (other than registrant).
   *
   * <p>This field is marked with {@literal @}Ignore so that {@link DomainBase} subclasses won't
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
   *
   * <p>This field is marked with {@literal @}Ignore so that {@link DomainBase} subclasses won't
   * persist it. Instead, the data in this field and in the {@link DomainBase#contacts} are both
   * stored in {@link DomainBase#allContacts} to allow for more efficient queries.
   */
  @Ignore
  //TODO(b/28713909): Make this a Ref<ContactResource>.
  ReferenceUnion<ContactResource> registrant;

  /** Authorization info (aka transfer secret) of the domain. */
  DomainAuthInfo authInfo;

  /**
   * Data used to construct DS records for this domain.
   *
   * <p>This is {@literal @}XmlTransient because it needs to be returned under the "extension" tag
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
   * @see google.registry.tldconfig.idn.IdnLabelValidator#findValidIdnTableForTld
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

  public ImmutableSet<Ref<HostResource>> getNameservers() {
    ImmutableSet.Builder<Ref<HostResource>> builder = new ImmutableSet.Builder<>();
    for (ReferenceUnion<HostResource> union : nullToEmptyImmutableCopy(nameservers)) {
      builder.add(union.getLinked());
    }
    return builder.build();
  }

  /** Loads and returns the fully qualified host names of all linked nameservers. */
  public ImmutableSet<String> loadNameserverFullyQualifiedHostNames() {
    return FluentIterable.from(ofy().load().refs(getNameservers()).values())
        .transform(
            new Function<HostResource, String>() {
              @Override
              public String apply(HostResource host) {
                return host.getFullyQualifiedHostName();
              }})
        .toSet();
  }

  public Ref<ContactResource> getRegistrant() {
    return registrant.getLinked();
  }

  public ImmutableSet<DesignatedContact> getContacts() {
    return nullToEmptyImmutableCopy(contacts);
  }

  public AuthInfo getAuthInfo() {
    return authInfo;
  }

  /** Returns all referenced contacts from this domain or application. */
  public ImmutableSet<Ref<ContactResource>> getReferencedContacts() {
    ImmutableSet.Builder<Ref<ContactResource>> contactsBuilder =
        new ImmutableSet.Builder<>();
    for (DesignatedContact designated : nullToEmptyImmutableCopy(allContacts)) {
      contactsBuilder.add(designated.getContactRef());
    }
    return contactsBuilder.build();
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
        registrant = ReferenceUnion.create(contact.getContactRef());
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
      checkArgumentNotNull(
          emptyToNull(instance.fullyQualifiedDomainName), "Missing fullyQualifiedDomainName");
      checkArgumentNotNull(instance.registrant, "Missing registrant");
      instance.tld = getTldFromSld(instance.fullyQualifiedDomainName);
      instance.allContacts = union(
              instance.getContacts(),
              DesignatedContact.create(REGISTRANT, instance.registrant.getLinked()));
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

    public B setRegistrant(Ref<ContactResource> registrant) {
      getInstance().registrant = ReferenceUnion.create(registrant);
      return thisCastToDerived();
    }

    public B setAuthInfo(DomainAuthInfo authInfo) {
      getInstance().authInfo = authInfo;
      return thisCastToDerived();
    }

    public B setNameservers(ImmutableSet<Ref<HostResource>> nameservers) {
      ImmutableSet.Builder<ReferenceUnion<HostResource>> builder = new ImmutableSet.Builder<>();
      for (Ref<HostResource> ref : nullToEmpty(nameservers)) {
        builder.add(ReferenceUnion.create(ref));
      }
      getInstance().nameservers = builder.build();
      return thisCastToDerived();
    }

    public B addNameservers(ImmutableSet<Ref<HostResource>> nameservers) {
      return setNameservers(
          ImmutableSet.copyOf(union(getInstance().getNameservers(), nameservers)));
    }

    public B removeNameservers(ImmutableSet<Ref<HostResource>> nameservers) {
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
