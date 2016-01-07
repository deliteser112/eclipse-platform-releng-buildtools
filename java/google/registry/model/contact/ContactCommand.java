// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.base.Preconditions.checkState;
import static google.registry.util.CollectionUtils.nullToEmpty;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource.Builder;
import google.registry.model.contact.PostalInfo.Type;
import google.registry.model.eppinput.ResourceCommand.AbstractSingleResourceCommand;
import google.registry.model.eppinput.ResourceCommand.ResourceCheck;
import google.registry.model.eppinput.ResourceCommand.ResourceCreateOrChange;
import google.registry.model.eppinput.ResourceCommand.ResourceUpdate;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/** A collection of {@link ContactResource} commands. */
public class ContactCommand {

  /** The fields on "chgType" from {@link "http://tools.ietf.org/html/rfc5733"}. */
  @XmlTransient
  public static class ContactCreateOrChange extends ImmutableObject
      implements ResourceCreateOrChange<ContactResource.Builder> {

    /** Postal info for the contact. */
    List<PostalInfo> postalInfo;

     /** Contact’s voice number. */
    ContactPhoneNumber voice;

    /** Contact’s fax number. */
    ContactPhoneNumber fax;

    /** Contact’s email address. */
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    String email;

    /** Authorization info (aka transfer secret) of the contact. */
    ContactAuthInfo authInfo;

    /** Disclosure policy. */
    Disclose disclose;

    /** Helper method to move between the postal infos list and the individual getters. */
    protected Map<Type, PostalInfo> getPostalInfosAsMap() {
      // There can be no more than 2 postalInfos (enforced by the schema), and if there are 2 they
      // must be of different types (not enforced). If the type is repeated, uniqueIndex will throw.
      checkState(nullToEmpty(postalInfo).size() <= 2);
      return Maps.uniqueIndex(nullToEmpty(postalInfo), new Function<PostalInfo, Type>() {
        @Override
        public Type apply(PostalInfo info) {
          return info.getType();
        }});
    }

    @Override
    public void applyTo(Builder builder) {
      if (authInfo != null) {
        builder.setAuthInfo(authInfo);
      }
      if (disclose != null) {
        builder.setDisclose(disclose);
      }
      if (email != null) {
        builder.setEmailAddress(email);
      }
      if (fax != null) {
        builder.setFaxNumber(fax);
      }
      if (voice != null) {
        builder.setVoiceNumber(voice);
      }
    }
  }

  /** An abstract contact command that contains authorization info. */
  @XmlTransient
  public static class AbstractContactAuthCommand extends AbstractSingleResourceCommand {
    /** Authorization info used to validate if client has permissions to perform this operation. */
    ContactAuthInfo authInfo;

    @Override
    public ContactAuthInfo getAuthInfo() {
      return authInfo;
    }
  }

  /**
   * A create command for a {@link ContactResource}, mapping "createType" from
   * {@link "http://tools.ietf.org/html/rfc5733"}.
   */
  @XmlType(propOrder = {"contactId", "postalInfo", "voice", "fax", "email", "authInfo", "disclose"})
  @XmlRootElement
  public static class Create extends ContactCreateOrChange
      implements SingleResourceCommand, ResourceCreateOrChange<ContactResource.Builder> {
    /**
     * Unique identifier for this contact.
     *
     * <p>This is only unique in the sense that for any given lifetime specified as the time range
     * from (creationTime, deletionTime) there can only be one contact in the datastore with this
     * id.  However, there can be many contacts with the same id and non-overlapping lifetimes.
     */
    @XmlElement(name = "id")
    String contactId;

    @Override
    public String getTargetId() {
      return contactId;
    }

    @Override
    public ContactAuthInfo getAuthInfo() {
      return authInfo;
    }

    @Override
    public void applyTo(ContactResource.Builder builder) {
      super.applyTo(builder);
      if (contactId != null) {
        builder.setContactId(contactId);
      }
      Map<Type, PostalInfo> postalInfosAsMap = getPostalInfosAsMap();
      if (postalInfosAsMap.containsKey(Type.INTERNATIONALIZED)) {
        builder.setInternationalizedPostalInfo(postalInfosAsMap.get(Type.INTERNATIONALIZED));
      }
      if (postalInfosAsMap.containsKey(Type.LOCALIZED)) {
        builder.setLocalizedPostalInfo(postalInfosAsMap.get(Type.LOCALIZED));
      }
    }
  }

  /** A delete command for a {@link ContactResource}. */
  @XmlRootElement
  public static class Delete extends AbstractSingleResourceCommand {}

  /** An info request for a {@link ContactResource}. */
  @XmlRootElement
  @XmlType(propOrder = {"targetId", "authInfo"})
  public static class Info extends AbstractContactAuthCommand {}

  /** A check request for {@link ContactResource}. */
  @XmlRootElement
  public static class Check extends ResourceCheck {}

  /** A transfer operation for a {@link ContactResource}. */
  @XmlRootElement
  @XmlType(propOrder = {"targetId", "authInfo"})
  public static class Transfer extends AbstractContactAuthCommand {}

  /** An update to a {@link ContactResource}. */
  @XmlRootElement
  @XmlType(propOrder = {"targetId", "innerAdd", "innerRemove", "innerChange"})
  public static class Update
      extends ResourceUpdate<Update.AddRemove, ContactResource.Builder, Update.Change> {

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

    /** The inner change type on a contact update command. */
    public static class AddRemove extends ResourceUpdate.AddRemove {}

    /** The inner change type on a contact update command. */
    @XmlType(propOrder = {"postalInfo", "voice", "fax", "email", "authInfo", "disclose"})
    public static class Change extends ContactCreateOrChange {
      /**
       * The spec requires the following behaviors:
       * <ul>
       *   <li>If you update part of a postal info, the fields that you didn't update are unchanged.
       *   <li>If you update one postal info but not the other, the other is deleted.
       * </ul>
       * Therefore, if you want to preserve one postal info and update another you need to send the
       * update and also something that technically updates the preserved one, even if it only
       * "updates" it by setting just one field to the same value.
       */
      @Override
      public void applyTo(ContactResource.Builder builder) {
        super.applyTo(builder);
        Map<Type, PostalInfo> postalInfosAsMap = getPostalInfosAsMap();
        if (postalInfosAsMap.containsKey(Type.INTERNATIONALIZED)) {
          builder.overlayInternationalizedPostalInfo(postalInfosAsMap.get(Type.INTERNATIONALIZED));
          if (postalInfosAsMap.size() == 1) {
            builder.setLocalizedPostalInfo(null);
          }
        }
        if (postalInfosAsMap.containsKey(Type.LOCALIZED)) {
          builder.overlayLocalizedPostalInfo(postalInfosAsMap.get(Type.LOCALIZED));
          if (postalInfosAsMap.size() == 1) {
            builder.setInternationalizedPostalInfo(null);
          }
        }
      }
    }
  }
}
