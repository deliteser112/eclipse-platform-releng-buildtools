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

package google.registry.model.contact;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.EppResourceUtils.projectResourceOntoBuilderAtTime;
import static google.registry.model.ofy.Ofy.RECOMMENDED_MEMCACHE_EXPIRATION;

import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.IgnoreSave;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.condition.IfNull;
import google.registry.model.EppResource;
import google.registry.model.EppResource.ForeignKeyedEppResource;
import google.registry.model.annotations.ExternalMessagingName;
import google.registry.model.contact.PostalInfo.Type;
import google.registry.model.eppcommon.AuthInfo;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.joda.time.DateTime;

/** A persistable Contact resource including mutable and non-mutable fields. */
@XmlRootElement(name = "infData")
@XmlType(propOrder = {
    "contactId",
    "repoId",
    "status",
    "postalInfosAsList",
    "voice",
    "fax",
    "email",
    "currentSponsorClientId",
    "creationClientId",
    "creationTime",
    "lastEppUpdateClientId",
    "lastEppUpdateTime",
    "lastTransferTime",
    "authInfo",
    "disclose" })
@Cache(expirationSeconds = RECOMMENDED_MEMCACHE_EXPIRATION)
@Entity
@ExternalMessagingName("contact")
public class ContactResource extends EppResource implements ForeignKeyedEppResource {

  /**
   * Unique identifier for this contact.
   *
   * <p>This is only unique in the sense that for any given lifetime specified as the time range
   * from (creationTime, deletionTime) there can only be one contact in the datastore with this id.
   * However, there can be many contacts with the same id and non-overlapping lifetimes.
   */
  @XmlTransient
  String contactId;

  /**
   * Localized postal info for the contact. All contained values must be representable in the 7-bit
   * US-ASCII character set. Personal info; cleared by wipeOut().
   */
  @IgnoreSave(IfNull.class)
  @XmlTransient
  PostalInfo localizedPostalInfo;

  /** Internationalized postal info for the contact. Personal info; cleared by wipeOut(). */
  @IgnoreSave(IfNull.class)
  @XmlTransient
  PostalInfo internationalizedPostalInfo;

  /**
   * Contact name used for name searches. This is set automatically to be the internationalized
   * postal name, or if null, the localized postal name, or if that is null as well, null. Personal
   * info; cleared by wipeOut().
   */
  @Index
  @XmlTransient
  String searchName;

  /** Contact’s voice number. Personal info; cleared by wipeOut(). */
  @IgnoreSave(IfNull.class)
  ContactPhoneNumber voice;

  /** Contact’s fax number. Personal info; cleared by wipeOut(). */
  @IgnoreSave(IfNull.class)
  ContactPhoneNumber fax;

  /** Contact’s email address. Personal info; cleared by wipeOut(). */
  @IgnoreSave(IfNull.class)
  @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
  String email;

  /** Authorization info (aka transfer secret) of the contact. */
  ContactAuthInfo authInfo;

  // If any new fields are added which contain personal information, make sure they are cleared by
  // the wipeOut() function, so that data is not kept around for deleted contacts.

  /** Disclosure policy. */
  @IgnoreSave(IfNull.class)
  Disclose disclose;

  @XmlElement(name = "id")
  public String getContactId() {
    return contactId;
  }

  public PostalInfo getLocalizedPostalInfo() {
    return localizedPostalInfo;
  }

  public PostalInfo getInternationalizedPostalInfo() {
    return internationalizedPostalInfo;
  }

  public ContactPhoneNumber getVoiceNumber() {
    return voice;
  }

  public ContactPhoneNumber getFaxNumber() {
    return fax;
  }

  public String getEmailAddress() {
    return email;
  }

  public AuthInfo getAuthInfo() {
    return authInfo;
  }

  public Disclose getDisclose() {
    return disclose;
  }

  @Override
  public String getForeignKey() {
    return contactId;
  }

  /**
   * Postal info for the contact.
   *
   * <p>The XML marshalling expects the {@link PostalInfo} objects in a list, but we can't actually
   * persist them to datastore that way because Objectify can't handle collections of embedded
   * objects that themselves contain collections, and there's a list of streets inside. This method
   * transforms the persisted format to the XML format for marshalling.
   */
  @XmlElement(name = "postalInfo")
  public List<PostalInfo> getPostalInfosAsList() {
    return FluentIterable
        .from(Lists.newArrayList(localizedPostalInfo, internationalizedPostalInfo))
        .filter(Predicates.notNull())
        .toList();
  }

  @Override
  public ContactResource cloneProjectedAtTime(DateTime now) {
    Builder builder = this.asBuilder();
    projectResourceOntoBuilderAtTime(this, builder, now);
    return builder.build();
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link ContactResource}, since it is immutable. */
  public static class Builder extends EppResource.Builder<ContactResource, Builder> {
    public Builder() {}

    private Builder(ContactResource instance) {
      super(instance);
    }

    public Builder setContactId(String contactId) {
      getInstance().contactId = contactId;
      return this;
    }

    public Builder setLocalizedPostalInfo(PostalInfo localizedPostalInfo) {
      checkArgument(localizedPostalInfo == null
          || Type.LOCALIZED.equals(localizedPostalInfo.getType()));
      getInstance().localizedPostalInfo = localizedPostalInfo;
      return this;
    }

    public Builder setInternationalizedPostalInfo(PostalInfo internationalizedPostalInfo) {
      checkArgument(internationalizedPostalInfo == null
          || Type.INTERNATIONALIZED.equals(internationalizedPostalInfo.getType()));
      getInstance().internationalizedPostalInfo = internationalizedPostalInfo;
      return this;
    }

    public Builder overlayLocalizedPostalInfo(PostalInfo localizedPostalInfo) {
      return setLocalizedPostalInfo(getInstance().localizedPostalInfo == null
          ? localizedPostalInfo
          : getInstance().localizedPostalInfo.overlay(localizedPostalInfo));
    }

    public Builder overlayInternationalizedPostalInfo(PostalInfo internationalizedPostalInfo) {
      return setInternationalizedPostalInfo(getInstance().internationalizedPostalInfo == null
          ? internationalizedPostalInfo
          : getInstance().internationalizedPostalInfo.overlay(internationalizedPostalInfo));
    }

    public Builder setVoiceNumber(ContactPhoneNumber voiceNumber) {
      getInstance().voice = voiceNumber;
      return this;
    }

    public Builder setFaxNumber(ContactPhoneNumber faxNumber) {
      getInstance().fax = faxNumber;
      return this;
    }

    public Builder setEmailAddress(String emailAddress) {
      getInstance().email = emailAddress;
      return this;
    }

    public Builder setAuthInfo(ContactAuthInfo authInfo) {
      getInstance().authInfo = authInfo;
      return this;
    }

    public Builder setDisclose(Disclose disclose) {
      getInstance().disclose = disclose;
      return this;
    }

    @Override
    public Builder wipeOut() {
      this.setEmailAddress(null);
      this.setFaxNumber(null);
      this.setInternationalizedPostalInfo(null);
      this.setLocalizedPostalInfo(null);
      this.setVoiceNumber(null);
      return super.wipeOut();
    }

    @Override
    public ContactResource build() {
      // Set the searchName using the internationalized and localized postal info names.
      if ((getInstance().internationalizedPostalInfo != null)
          && (getInstance().internationalizedPostalInfo.getName() != null)) {
        getInstance().searchName = getInstance().internationalizedPostalInfo.getName();
      } else if ((getInstance().localizedPostalInfo != null)
          && (getInstance().localizedPostalInfo.getName() != null)) {
        getInstance().searchName = getInstance().localizedPostalInfo.getName();
      } else {
        getInstance().searchName = null;
      }
      return super.build();
    }
  }
}
