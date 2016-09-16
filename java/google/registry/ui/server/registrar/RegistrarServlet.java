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

package google.registry.ui.server.registrar;

import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Sets.difference;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.security.JsonResponseHelper.Status.SUCCESS;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.googlecode.objectify.Work;
import google.registry.config.RegistryConfig;
import google.registry.config.RegistryEnvironment;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.security.JsonResponseHelper;
import google.registry.ui.forms.FormException;
import google.registry.ui.server.RegistrarFormFields;
import google.registry.util.CidrAddressBlock;
import google.registry.util.CollectionUtils;
import google.registry.util.DiffUtils;
import google.registry.util.SendEmailUtils;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;

/**
 * Admin servlet that allows creating or updating a registrar. Deletes are not allowed so as to
 * preserve history.
 */
public class RegistrarServlet extends ResourceServlet {

  private static final RegistryConfig CONFIG = RegistryEnvironment.get().config();

  private static final Predicate<RegistrarContact> HAS_PHONE = new Predicate<RegistrarContact>() {
    @Override
    public boolean apply(RegistrarContact contact) {
      return contact.getPhoneNumber() != null;
    }};

  /** Thrown when a set of contacts doesn't meet certain constraints. */
  private static class ContactRequirementException extends FormException {
    ContactRequirementException(String msg) {
      super(msg);
    }

    ContactRequirementException(RegistrarContact.Type type) {
      super("Must have at least one " + type.getDisplayName() + " contact");
    }
  }

  @Override
  public Map<String, Object> read(HttpServletRequest req, Map<String, ?> args) {
    String clientId = sessionUtils.getRegistrarClientId(req);
    Registrar registrar = getCheckedRegistrar(clientId);
    return JsonResponseHelper.create(SUCCESS, "Success", registrar.toJsonMap());
  }

  @Override
  Map<String, Object> update(HttpServletRequest req, final Map<String, ?> args) {
    final String clientId = sessionUtils.getRegistrarClientId(req);
    return ofy().transact(new Work<Map<String, Object>>() {
      @Override
      public Map<String, Object> run() {
        Registrar existingRegistrar = getCheckedRegistrar(clientId);
        ImmutableSet<RegistrarContact> oldContacts = existingRegistrar.getContacts();
        Map<String, Object> existingRegistrarMap =
            expandRegistrarWithContacts(existingRegistrar, oldContacts);
        Registrar.Builder builder = existingRegistrar.asBuilder();
        ImmutableSet<RegistrarContact> updatedContacts = update(existingRegistrar, builder, args);
        if (!updatedContacts.isEmpty()) {
          builder.setContactsRequireSyncing(true);
        }
        Registrar updatedRegistrar = builder.build();
        ofy().save().entity(updatedRegistrar);
        if (!updatedContacts.isEmpty()) {
          checkContactRequirements(oldContacts, updatedContacts);
          RegistrarContact.updateContacts(updatedRegistrar, updatedContacts);
        }
        // Update the registrar map with updated contacts to bypass Objectify caching issues that
        // come into play with calling getContacts().
        Map<String, Object> updatedRegistrarMap =
            expandRegistrarWithContacts(updatedRegistrar, updatedContacts);
        sendExternalUpdatesIfNecessary(
            updatedRegistrar.getRegistrarName(),
            existingRegistrarMap,
            updatedRegistrarMap);
        return JsonResponseHelper.create(
            SUCCESS,
            "Saved " + clientId,
            updatedRegistrar.toJsonMap());
      }});
  }

  private Map<String, Object> expandRegistrarWithContacts(
      Registrar registrar, Iterable<RegistrarContact> contacts) {
    ImmutableSet<Map<String, Object>> expandedContacts = FluentIterable.from(contacts)
        .transform(new Function<RegistrarContact, Map<String, Object>>() {
          @Override
          public Map<String, Object>  apply(RegistrarContact contact) {
            return contact.toDiffableFieldMap();
          }})
      .toSet();
    // Use LinkedHashMap here to preserve ordering; null values mean we can't use ImmutableMap.
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.putAll(registrar.toDiffableFieldMap());
    result.put("contacts", expandedContacts);
    return result;
  }

  /**
   * Determines if any changes were made to the registrar besides the lastUpdateTime, and if so,
   * sends an email with a diff of the changes to the configured notification email address and
   * enqueues a task to re-sync the registrar sheet.
   */
  private void sendExternalUpdatesIfNecessary(
      String registrarName,
      Map<String, Object> existingRegistrar,
      Map<String, Object> updatedRegistrar) {
    Map<?, ?> diffs = DiffUtils.deepDiff(existingRegistrar, updatedRegistrar, true);
    @SuppressWarnings("unchecked")
    Set<String> changedKeys = (Set<String>) diffs.keySet();
    if (CollectionUtils.difference(changedKeys, "lastUpdateTime").isEmpty()) {
      return;
    }
    SyncRegistrarsSheetAction.enqueueBackendTask();
    ImmutableList<String> toEmailAddress = CONFIG.getRegistrarChangesNotificationEmailAddresses();
    if (!toEmailAddress.isEmpty()) {
      SendEmailUtils.sendEmail(
          toEmailAddress,
          String.format("Registrar %s updated", registrarName),
          "The following changes were made to the registrar:\n"
              + DiffUtils.prettyPrintDiffedMap(diffs, null));
    }
  }

  private Registrar getCheckedRegistrar(String clientId) {
    return checkExists(Registrar.loadByClientId(clientId),
        "No registrar exists with the given client id: " + clientId);
  }

  /**
   * Enforces business logic checks on registrar contacts.
   *
   * @throws FormException if the checks fail.
   */
  void checkContactRequirements(
      Set<RegistrarContact> existingContacts, Set<RegistrarContact> updatedContacts) {
    // Check that no two contacts use the same email address.
    Set<String> emails = new HashSet<>();
    for (RegistrarContact contact : updatedContacts) {
      if (!emails.add(contact.getEmailAddress())) {
        throw new ContactRequirementException(String.format(
            "One email address (%s) cannot be used for multiple contacts",
            contact.getEmailAddress()));
      }
    }
    // Check that required contacts don't go away, once they are set.
    Multimap<RegistrarContact.Type, RegistrarContact> oldContactsByType = HashMultimap.create();
    for (RegistrarContact contact : existingContacts) {
      for (RegistrarContact.Type t : contact.getTypes()) {
        oldContactsByType.put(t, contact);
      }
    }
    Multimap<RegistrarContact.Type, RegistrarContact> newContactsByType = HashMultimap.create();
    for (RegistrarContact contact : updatedContacts) {
      for (RegistrarContact.Type t : contact.getTypes()) {
        newContactsByType.put(t, contact);
      }
    }
    for (RegistrarContact.Type t
        : difference(oldContactsByType.keySet(), newContactsByType.keySet())) {
      if (t.isRequired()) {
        throw new ContactRequirementException(t);
      }
    }
    // Ensure at least one tech contact has a phone number if one was present before.
    if (any(oldContactsByType.get(RegistrarContact.Type.TECH), HAS_PHONE)
        && !any(newContactsByType.get(RegistrarContact.Type.TECH), HAS_PHONE)) {
      throw new ContactRequirementException(String.format(
          "At least one %s contact must have a phone number",
          RegistrarContact.Type.TECH.getDisplayName()));
    }
  }

  /**
   * Updates a registrar builder with the supplied args from the http request, and returns a list of
   * the new registrar contacts.
   */
  public static ImmutableSet<RegistrarContact> update(
      Registrar existingRegistrarObj, Registrar.Builder builder, Map<String, ?> args) {

    // WHOIS
    builder.setWhoisServer(
        RegistrarFormFields.WHOIS_SERVER_FIELD.extractUntyped(args).orNull());
    builder.setReferralUrl(
        RegistrarFormFields.REFERRAL_URL_FIELD.extractUntyped(args).orNull());
    for (String email :
        RegistrarFormFields.EMAIL_ADDRESS_FIELD.extractUntyped(args).asSet()) {
      builder.setEmailAddress(email);
    }
    builder.setPhoneNumber(
        RegistrarFormFields.PHONE_NUMBER_FIELD.extractUntyped(args).orNull());
    builder.setFaxNumber(
        RegistrarFormFields.FAX_NUMBER_FIELD.extractUntyped(args).orNull());
    builder.setLocalizedAddress(
        RegistrarFormFields.L10N_ADDRESS_FIELD.extractUntyped(args).orNull());

    // Security
    builder.setIpAddressWhitelist(
        RegistrarFormFields.IP_ADDRESS_WHITELIST_FIELD.extractUntyped(args).or(
            ImmutableList.<CidrAddressBlock>of()));
    for (String certificate
        : RegistrarFormFields.CLIENT_CERTIFICATE_FIELD.extractUntyped(args).asSet()) {
      builder.setClientCertificate(certificate, ofy().getTransactionTime());
    }
    for (String certificate
        : RegistrarFormFields.FAILOVER_CLIENT_CERTIFICATE_FIELD.extractUntyped(args).asSet()) {
      builder.setFailoverClientCertificate(certificate, ofy().getTransactionTime());
    }

    builder.setUrl(
        RegistrarFormFields.URL_FIELD.extractUntyped(args).orNull());
    builder.setReferralUrl(
        RegistrarFormFields.REFERRAL_URL_FIELD.extractUntyped(args).orNull());

    // Contact
    ImmutableSet.Builder<RegistrarContact> contacts = new ImmutableSet.Builder<>();
    for (RegistrarContact.Builder contactBuilder
        : concat(RegistrarFormFields.CONTACTS_FIELD.extractUntyped(args).asSet())) {
      contacts.add(contactBuilder.setParent(existingRegistrarObj).build());
    }

    return contacts.build();
  }
}
