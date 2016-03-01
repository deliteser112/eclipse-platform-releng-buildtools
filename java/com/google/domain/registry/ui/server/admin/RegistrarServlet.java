// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.ui.server.admin;

import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.export.sheet.SyncRegistrarsSheetTask;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.ui.forms.FormFields;
import com.google.domain.registry.ui.server.RegistrarFormFields;

import com.googlecode.objectify.VoidWork;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * Admin servlet that allows creating or updating a registrar. Deletes are not allowed so as to
 * preserve history.
 */
public class RegistrarServlet extends AdminResourceServlet {

  @Override
  public Map<String, Object> create(HttpServletRequest req, final Map<String, ?> args) {
    final String clientIdentifier = FormFields.CLID.convert(parseId(req)).get();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        Registrar registrar = new Registrar.Builder()
            .setClientIdentifier(clientIdentifier)
            .setRegistrarName(clientIdentifier)
            .setType(Registrar.Type.TEST)
            .setState(Registrar.State.ACTIVE)
            .setAllowedTlds(ImmutableSet.<String>of())
            .build();
        Registrar.Builder builder = registrar.asBuilder();
        Set<RegistrarContact> contacts = update(registrar, builder, args);
        registrar = builder.build();
        ofy().save().entity(registrar);
        if (!contacts.isEmpty()) {
          RegistrarContact.updateContacts(registrar, contacts);
        }
      }});
    return ImmutableMap.<String, Object>of("results", ImmutableList.of(clientIdentifier + ": ok"));
  }

  @Override
  public Map<String, Object> read(HttpServletRequest req, Map<String, ?> args) {
    String clientIdentifier = parseId(req);
    if (clientIdentifier == null) {
      List<Map<String, ?>> registrars = new ArrayList<>();
      for (Registrar registrar : Registrar.loadAll()) {
        registrars.add(registrar.toJsonMap());
      }
      return ImmutableMap.<String, Object>of("set", registrars);
    }
    Registrar registrar = Registrar.loadByClientId(clientIdentifier);
    checkExists(registrar, "No registrar exists with the given client id: " + clientIdentifier);
    return ImmutableMap.<String, Object>of("item", registrar.toJsonMap());
  }

  @Override
  public Map<String, Object> update(HttpServletRequest req, final Map<String, ?> args) {
    final String clientIdentifier = checkParseId(req);
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        Registrar registrar = Registrar.loadByClientId(clientIdentifier);
        Registrar.Builder builder = checkExists(
            registrar,
            "No registrar exists with the given client id: " + clientIdentifier)
                .asBuilder();
        Set<RegistrarContact> updatedContacts = update(registrar, builder, args);
        if (!updatedContacts.isEmpty()) {
          builder.setContactsRequireSyncing(true);
        }
        Registrar updatedRegistrar = builder.build();
        ofy().save().entity(updatedRegistrar);
        if (!updatedContacts.isEmpty()) {
          RegistrarContact.updateContacts(updatedRegistrar, updatedContacts);
        }
        SyncRegistrarsSheetTask.enqueueBackendTask();
      }});
    return ImmutableMap.<String, Object>of("results", ImmutableList.of(clientIdentifier + ": ok"));
  }

  /**
   * Admin fields are updated and then a chained call is made to
   * {@link com.google.domain.registry.ui.server.registrar.RegistrarServlet#update(
   *     Registrar, Registrar.Builder, Map)}
   * for the shared fields.
   */
  private static Set<RegistrarContact> update(
      Registrar existingRegistrarObj, Registrar.Builder builder, Map<String, ?> args) {
    // Admin only settings
    for (Registrar.State state :
        RegistrarFormFields.STATE_FIELD.extractUntyped(args).asSet()) {
      builder.setState(state);
    }
    builder.setAllowedTlds(
        RegistrarFormFields.ALLOWED_TLDS_FIELD.extractUntyped(args).or(ImmutableSet.<String>of()));
    Boolean blockPremiumNames =
        RegistrarFormFields.BLOCK_PREMIUM_NAMES_FIELD.extractUntyped(args).orNull();
    builder.setBlockPremiumNames(blockPremiumNames == null ? false : blockPremiumNames);
    for (String password :
        RegistrarFormFields.PASSWORD_FIELD.extractUntyped(args).asSet()) {
      builder.setPassword(password);
    }
    for (Long billingIdentifier :
        RegistrarFormFields.BILLING_IDENTIFIER_FIELD.extractUntyped(args).asSet()) {
      builder.setBillingIdentifier(billingIdentifier);
    }

    // Resources
    for (String driveFolderId :
        RegistrarFormFields.DRIVE_FOLDER_ID_FIELD.extractUntyped(args).asSet()) {
      builder.setDriveFolderId(driveFolderId);
    }

    // WHOIS
    for (String registrarName :
        RegistrarFormFields.NAME_FIELD.extractUntyped(args).asSet()) {
      builder.setRegistrarName(registrarName);
    }
    for (Long ianaIdentifier :
        RegistrarFormFields.IANA_IDENTIFIER_FIELD.extractUntyped(args).asSet()) {
      builder.setIanaIdentifier(ianaIdentifier);
    }
    builder.setIcannReferralEmail(
        RegistrarFormFields.ICANN_REFERRAL_EMAIL_FIELD.extractUntyped(args).get());

    // Security
    for (String phonePasscode :
        RegistrarFormFields.PHONE_PASSCODE_FIELD.extractUntyped(args).asSet()) {
      builder.setPhonePasscode(phonePasscode);
    }

    // Will this ever get used?
    builder.setUrl(
        RegistrarFormFields.URL_FIELD.extractUntyped(args).orNull());

    return com.google.domain.registry.ui.server.registrar.RegistrarServlet.update(
        existingRegistrarObj, builder, args);
  }
}
