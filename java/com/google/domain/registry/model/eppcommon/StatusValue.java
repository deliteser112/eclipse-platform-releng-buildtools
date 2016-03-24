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

package com.google.domain.registry.model.eppcommon;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.base.Converter;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.translators.EnumToAttributeAdapter.EppEnum;
import com.google.domain.registry.model.translators.StatusValueAdapter;

import com.googlecode.objectify.annotation.Embed;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Represents an EPP status value for hosts, contacts, and domains, as defined in RFC 5731, 5732,
 * and 5733. The values here are the union of all 3 sets of status values.
 *
 * <p>The RFCs define extra optional metadata (language and message) that we don't use and therefore
 * don't model.
 *
 * <p>Note that {@code StatusValue.LINKED} should never be stored. Rather, it should be calculated
 * on the fly whenever needed using an eventually consistent query (i.e. in info flows).
 *
 * @see "https://www.icann.org/resources/pages/epp-status-codes-2014-06-16-en"
 */
@XmlJavaTypeAdapter(StatusValueAdapter.class)
public enum StatusValue implements EppEnum {

  CLIENT_DELETE_PROHIBITED,
  CLIENT_HOLD,
  CLIENT_RENEW_PROHIBITED,
  CLIENT_TRANSFER_PROHIBITED,
  CLIENT_UPDATE_PROHIBITED,
  INACTIVE,
  LINKED,
  OK,
  PENDING_CREATE,
  PENDING_DELETE,
  PENDING_TRANSFER,
  PENDING_UPDATE,
  SERVER_DELETE_PROHIBITED,
  SERVER_HOLD,
  SERVER_RENEW_PROHIBITED,
  SERVER_TRANSFER_PROHIBITED,
  SERVER_UPDATE_PROHIBITED;

  private final String xmlName = UPPER_UNDERSCORE.to(LOWER_CAMEL, name());

  @Override
  public String getXmlName() {
    return xmlName;
  }

  public boolean isClientSettable() {
    // This is the actual definition of client-settable statuses; see RFC5730 section 2.3.
    return xmlName.startsWith("client");
  }

  public boolean isChargedStatus() {
    return xmlName.startsWith("server") && xmlName.endsWith("Prohibited");
  }

  public static StatusValue fromXmlName(String xmlName) {
    return StatusValue.valueOf(LOWER_CAMEL.to(UPPER_UNDERSCORE, nullToEmpty(xmlName)));
  }

  /** Stripped down version of the legacy format of status values for migration purposes. */
  // TODO(b/25442343): Remove this.
  @Embed
  public static class LegacyStatusValue extends ImmutableObject {
    String xmlStatusValue;
  }

  /** Converter between the old and new formats. */
  // TODO(b/25442343): Remove this.
  public static final Converter<StatusValue, LegacyStatusValue> LEGACY_CONVERTER =
      new Converter<StatusValue, LegacyStatusValue>() {
        @Override
        protected LegacyStatusValue doForward(StatusValue status) {
          LegacyStatusValue legacyStatus = new LegacyStatusValue();
          legacyStatus.xmlStatusValue = status.xmlName;
          return legacyStatus;
        }

        @Override
        protected StatusValue doBackward(LegacyStatusValue legacyStatus) {
          return StatusValue.fromXmlName(legacyStatus.xmlStatusValue);
        }
      };
}
