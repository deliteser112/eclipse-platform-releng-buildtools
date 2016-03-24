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

package com.google.domain.registry.whois;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;

import org.joda.time.DateTime;

import java.util.Set;

import javax.annotation.Nullable;

/** Container for WHOIS responses to registrar lookup queries. */
class RegistrarWhoisResponse extends WhoisResponseImpl {

  /** Registrar which was the target of this WHOIS command. */
  private final Registrar registrar;

  /**
   * Used in the emitter below to signal either admin or tech
   * contacts.  NB, this is purposely distinct from the
   * RegistrarContact.Type.{ADMIN,TECH} as they don't carry equivalent
   * meaning in our system. Sigh.
   */
  private enum AdminOrTech { ADMIN, TECH }

  /** Creates a new WHOIS registrar response on the given registrar object. */
  RegistrarWhoisResponse(Registrar registrar, DateTime timestamp) {
    super(timestamp);
    this.registrar = checkNotNull(registrar, "registrar");
  }

  @Override
  public String getPlainTextOutput(boolean preferUnicode) {
    Set<RegistrarContact> contacts = registrar.getContacts();
    return new RegistrarEmitter()
        .emitField("Registrar Name", registrar.getRegistrarName())
        .emitAddress(null, chooseByUnicodePreference(
            preferUnicode,
            registrar.getLocalizedAddress(),
            registrar.getInternationalizedAddress()))
        .emitPhonesAndEmail(
            registrar.getPhoneNumber(),
            registrar.getFaxNumber(),
            registrar.getEmailAddress())
        .emitField("Registrar WHOIS Server", registrar.getWhoisServer())
        .emitField("Registrar URL", registrar.getReferralUrl())
        .emitRegistrarContacts("Admin", contacts, AdminOrTech.ADMIN)
        .emitRegistrarContacts("Technical", contacts,  AdminOrTech.TECH)
        .emitFooter(getTimestamp())
        .toString();
  }

  /** An emitter with logic for registrars. */
  class RegistrarEmitter extends Emitter<RegistrarEmitter> {
    /** Emits the registrar contact of the given type. */
    RegistrarEmitter emitRegistrarContacts(
        String contactLabel,
        Iterable<RegistrarContact> contacts,
        AdminOrTech type) {
      for (RegistrarContact contact : contacts) {
        if ((type == AdminOrTech.ADMIN && contact.getVisibleInWhoisAsAdmin())
            || (type == AdminOrTech.TECH && contact.getVisibleInWhoisAsTech())) {
          emitField(contactLabel + " Contact", contact.getName())
              .emitPhonesAndEmail(
                  contact.getPhoneNumber(),
                  contact.getFaxNumber(),
                  contact.getEmailAddress());
        }
      }
      return this;
    }

    /** Emits the registrar contact of the given type. */
    RegistrarEmitter emitPhonesAndEmail(
        @Nullable String phone, @Nullable String fax, @Nullable String email) {
      return emitField("Phone Number", phone)
          .emitField("Fax Number", fax)
          .emitField("Email", email);
    }
  }
}
