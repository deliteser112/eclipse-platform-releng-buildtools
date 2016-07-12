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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Maps.transformValues;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.difference;
import static google.registry.util.CollectionUtils.nullSafeImmutableCopy;
import static google.registry.util.CollectionUtils.nullToEmptyImmutableCopy;
import static google.registry.util.CollectionUtils.union;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Ref;
import com.googlecode.objectify.Work;
import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppinput.ResourceCommand.AbstractSingleResourceCommand;
import google.registry.model.eppinput.ResourceCommand.ResourceCheck;
import google.registry.model.eppinput.ResourceCommand.ResourceCreateOrChange;
import google.registry.model.eppinput.ResourceCommand.ResourceUpdate;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.host.HostResource;
import google.registry.model.index.ForeignKeyIndex;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

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
    public T cloneAndLinkReferences(DateTime now) throws InvalidReferencesException;
  }

  /** The fields on "chgType" from {@link "http://tools.ietf.org/html/rfc5731"}. */
  @XmlTransient
  public static class DomainCreateOrChange<B extends DomainBase.Builder<?, ?>>
      extends ImmutableObject implements ResourceCreateOrChange<B> {

    /** The contactId of the registrant who registered this domain. */
    @XmlElement(name = "registrant")
    String registrantContactId;

    /** A resolved reference to the registrant who registered this domain. */
    @XmlTransient
    Ref<ContactResource> registrant;

    /** Authorization info (aka transfer secret) of the domain. */
    DomainAuthInfo authInfo;

    public String getRegistrantContactId() {
      return registrantContactId;
    }

    public Ref<ContactResource> getRegistrant() {
      return registrant;
    }

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
      "fullyQualifiedDomainName",
      "period",
      "nameserverFullyQualifiedHostNames",
      "registrantContactId",
      "foreignKeyedDesignatedContacts",
      "authInfo"})
  public static class Create
      extends DomainCreateOrChange<DomainBase.Builder<?, ?>>
      implements CreateOrUpdate<Create> {

    /** Fully qualified domain name, which serves as a unique identifier for this domain. */
    @XmlElement(name = "name")
    String fullyQualifiedDomainName;

    /** Fully qualified host names of the hosts that are the nameservers for the domain. */
    @XmlElementWrapper(name = "ns")
    @XmlElement(name = "hostObj")
    Set<String> nameserverFullyQualifiedHostNames;

    /** Resolved references to hosts that are the nameservers for the domain. */
    @XmlTransient
    Set<Ref<HostResource>> nameservers;

    /** Foreign keyed associated contacts for the domain (other than registrant). */
    @XmlElement(name = "contact")
    Set<ForeignKeyedDesignatedContact> foreignKeyedDesignatedContacts;

    /** Resolved references to associated contacts for the domain (other than registrant). */
    @XmlTransient
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

    public ImmutableSet<String> getNameserverFullyQualifiedHostNames() {
      return nullSafeImmutableCopy(nameserverFullyQualifiedHostNames);
    }

    public ImmutableSet<Ref<HostResource>> getNameservers() {
      return nullSafeImmutableCopy(nameservers);
    }

    public ImmutableSet<DesignatedContact> getContacts() {
      return nullSafeImmutableCopy(contacts);
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
    public Create cloneAndLinkReferences(DateTime now) throws InvalidReferencesException {
      Create clone = clone(this);
      clone.nameservers = linkHosts(clone.nameserverFullyQualifiedHostNames, now);
      if (registrantContactId == null) {
        clone.contacts = linkContacts(clone.foreignKeyedDesignatedContacts, now);
      } else {
        // Load the registrant and contacts in one shot.
        ForeignKeyedDesignatedContact registrantPlaceholder = new ForeignKeyedDesignatedContact();
        registrantPlaceholder.contactId = clone.registrantContactId;
        registrantPlaceholder.type = DesignatedContact.Type.REGISTRANT;
        Set<DesignatedContact> contacts = linkContacts(
            union(clone.foreignKeyedDesignatedContacts, registrantPlaceholder),
            now);
        for (DesignatedContact contact : contacts) {
          if (DesignatedContact.Type.REGISTRANT.equals(contact.getType())) {
            clone.registrant = contact.getContactRef();
            clone.contacts = difference(contacts, contact);
            break;
          }
        }
      }
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
    @XmlType(propOrder = {
        "nameserverFullyQualifiedHostNames",
        "foreignKeyedDesignatedContacts",
        "statusValues"})
    public static class AddRemove extends ResourceUpdate.AddRemove {
      /** Fully qualified host names of the hosts that are the nameservers for the domain. */
      @XmlElementWrapper(name = "ns")
      @XmlElement(name = "hostObj")
      Set<String> nameserverFullyQualifiedHostNames;

      /** Resolved references to hosts that are the nameservers for the domain. */
      @XmlTransient
      Set<Ref<HostResource>> nameservers;

      /** Foreign keyed associated contacts for the domain (other than registrant). */
      @XmlElement(name = "contact")
      Set<ForeignKeyedDesignatedContact> foreignKeyedDesignatedContacts;

      /** Resolved references to associated contacts for the domain (other than registrant). */
      @XmlTransient
      Set<DesignatedContact> contacts;

      public ImmutableSet<String> getNameserverFullyQualifiedHostNames() {
        return nullSafeImmutableCopy(nameserverFullyQualifiedHostNames);
      }

      public ImmutableSet<Ref<HostResource>> getNameservers() {
        return nullToEmptyImmutableCopy(nameservers);
      }

      public ImmutableSet<DesignatedContact> getContacts() {
        return nullToEmptyImmutableCopy(contacts);
      }

      /** Creates a copy of this {@link AddRemove} with hard links to hosts and contacts. */
      private AddRemove cloneAndLinkReferences(DateTime now) throws InvalidReferencesException {
        AddRemove clone = clone(this);
        clone.nameservers = linkHosts(clone.nameserverFullyQualifiedHostNames, now);
        clone.contacts = linkContacts(clone.foreignKeyedDesignatedContacts, now);
        return clone;
      }
    }

    /** The inner change type on a domain update command. */
    @XmlType(propOrder = {"registrantContactId", "authInfo"})
    public static class Change extends DomainCreateOrChange<DomainBase.Builder<?, ?>> {
      /** Creates a copy of this {@link Change} with hard links to hosts and contacts. */
      Change cloneAndLinkReferences(DateTime now) throws InvalidReferencesException {
        Change clone = clone(this);
        clone.registrant = clone.registrantContactId == null
            ? null
            : getOnlyElement(
                loadReferences(
                    ImmutableSet.of(clone.registrantContactId), ContactResource.class, now)
                        .values());
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
     *
     * <p>As a side effect, this will turn null innerAdd/innerRemove/innerChange into empty versions
     * of those classes, which is harmless because the getters do that anyways.
     */
    @Override
    public Update cloneAndLinkReferences(DateTime now) throws InvalidReferencesException {
      Update clone = clone(this);
      clone.innerAdd = clone.getInnerAdd().cloneAndLinkReferences(now);
      clone.innerRemove = clone.getInnerRemove().cloneAndLinkReferences(now);
      clone.innerChange = clone.getInnerChange().cloneAndLinkReferences(now);
      return clone;
    }
  }

  private static Set<Ref<HostResource>> linkHosts(
      Set<String> fullyQualifiedHostNames, DateTime now) throws InvalidReferencesException {
    if (fullyQualifiedHostNames == null) {
      return null;
    }
    return ImmutableSet.copyOf(
        loadReferences(fullyQualifiedHostNames, HostResource.class, now).values());
  }

  private static Set<DesignatedContact> linkContacts(
      Set<ForeignKeyedDesignatedContact> contacts, DateTime now) throws InvalidReferencesException {
    if (contacts == null) {
      return null;
    }
    ImmutableSet.Builder<String> foreignKeys = new ImmutableSet.Builder<>();
    for (ForeignKeyedDesignatedContact contact : contacts) {
      foreignKeys.add(contact.contactId);
    }
    ImmutableMap<String, Ref<ContactResource>> loadedContacts =
        loadReferences(foreignKeys.build(), ContactResource.class, now);
    ImmutableSet.Builder<DesignatedContact> linkedContacts = new ImmutableSet.Builder<>();
    for (ForeignKeyedDesignatedContact contact : contacts) {
      linkedContacts.add(DesignatedContact.create(
          contact.type, loadedContacts.get(contact.contactId)));
    }
    return linkedContacts.build();
  }

  /** Load references to resources by their foreign keys. */
  private static <T extends EppResource> ImmutableMap<String, Ref<T>> loadReferences(
      final Set<String> foreignKeys, final Class<T> clazz, final DateTime now)
      throws InvalidReferencesException {
    Map<String, ForeignKeyIndex<T>> fkis = ofy().doTransactionless(
        new Work<Map<String, ForeignKeyIndex<T>>>() {
          @Override
          public Map<String, ForeignKeyIndex<T>> run() {
            return ForeignKeyIndex.load(clazz, foreignKeys, now);
          }});
    if (!fkis.keySet().equals(foreignKeys)) {
      throw new InvalidReferencesException(
          clazz, ImmutableSet.copyOf(difference(foreignKeys, fkis.keySet())));
    }
    return ImmutableMap.copyOf(transformValues(
        fkis,
        new Function<ForeignKeyIndex<T>, Ref<T>>() {
          @Override
          public Ref<T> apply(ForeignKeyIndex<T> fki) {
            return fki.getReference();
          }}));
  }

  /** Exception to throw when referenced objects don't exist. */
  public static class InvalidReferencesException extends Exception {
    private final ImmutableSet<String> foreignKeys;
    private final Class<?> type;

    InvalidReferencesException(Class<?> type, ImmutableSet<String> foreignKeys) {
      this.type = checkNotNull(type);
      this.foreignKeys = foreignKeys;
    }

    public ImmutableSet<String> getForeignKeys() {
      return foreignKeys;
    }

    public Class<?> getType() {
      return type;
    }
  }
}
