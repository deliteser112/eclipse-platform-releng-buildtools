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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newHashSet;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Enums;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.common.GaeUserIdConverter;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.registrar.RegistrarContact.Builder;
import google.registry.tools.params.OptionalPhoneNumberParameter;
import google.registry.tools.params.PathParameter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/** Command for CRUD operations on {@link Registrar} contact list fields. */
@Parameters(
    separators = " =",
    commandDescription = "Create/read/update/delete the various contact lists for a Registrar.")
final class RegistrarContactCommand extends MutatingCommand {

  private enum Mode { LIST, CREATE, UPDATE, DELETE }

  @Parameter(
      description = "Client identifier of the registrar account.",
      required = true)
  private List<String> mainParameters;

  @Parameter(
      names = "--mode",
      description = "Type of operation you want to perform (LIST, CREATE, UPDATE, or DELETE).",
      required = true)
  private Mode mode;

  @Nullable
  @Parameter(
      names = "--name",
      description = "Contact name.")
  private String name;

  @Nullable
  @Parameter(
      names = "--contact_type",
      description = "Type of communications for this contact; separate multiple with commas."
          + " Allowed values are ABUSE, ADMIN, BILLING, LEGAL, MARKETING, TECH, WHOIS.")
  private List<String> contactTypeNames;

  @Nullable
  @Parameter(
      names = "--email",
      description = "Contact email address.")
  private String email;

  @Nullable
  @Parameter(
      names = "--phone",
      description = "E.164 phone number, e.g. +1.2125650666",
      converter = OptionalPhoneNumberParameter.class,
      validateWith = OptionalPhoneNumberParameter.class)
  private Optional<String> phone;

  @Nullable
  @Parameter(
      names = "--fax",
      description = "E.164 fax number, e.g. +1.2125650666",
      converter = OptionalPhoneNumberParameter.class,
      validateWith = OptionalPhoneNumberParameter.class)
  private Optional<String> fax;

  @Nullable
  @Parameter(
      names = "--allow_console_access",
      description = "Enable or disable access to the registrar console for this contact.",
      arity = 1)
  private Boolean allowConsoleAccess;

  @Nullable
  @Parameter(
      names = "--visible_in_whois_as_admin",
      description = " Whether this contact is publically visible in WHOIS results as an "
          + "Admin contact.",
      arity = 1)
  private Boolean visibleInWhoisAsAdmin;

  @Nullable
  @Parameter(
      names = "--visible_in_whois_as_tech",
      description = " Whether this contact is publically visible in WHOIS results as a "
          + "Tech contact.",
      arity = 1)
  private Boolean visibleInWhoisAsTech;

  @Parameter(
      names = {"-o", "--output"},
      description = "Output file when --mode=LIST",
      validateWith = PathParameter.OutputFile.class)
  private Path output = Paths.get("/dev/stdout");

  @Nullable
  private Set<RegistrarContact.Type> contactTypes;

  @Override
  protected void init() throws Exception {
    checkArgument(mainParameters.size() == 1,
        "Must specify exactly one client identifier: %s", ImmutableList.copyOf(mainParameters));
    String clientId = mainParameters.get(0);
    Registrar registrar =
        checkNotNull(Registrar.loadByClientId(clientId), "Registrar %s not found", clientId);
    contactTypes =
        newHashSet(
            transform(
                nullToEmpty(contactTypeNames), Enums.stringConverter(RegistrarContact.Type.class)));
    ImmutableSet<RegistrarContact> contacts = registrar.getContacts();
    Map<String, RegistrarContact> contactsMap = new LinkedHashMap<>();
    for (RegistrarContact rc : contacts) {
      contactsMap.put(rc.getEmailAddress(), rc);
    }
    RegistrarContact oldContact;
    switch (mode) {
      case LIST:
        listContacts(contacts);
        break;
      case CREATE:
        stageEntityChange(null, createContact(registrar));
        break;
      case UPDATE:
        oldContact =
            checkNotNull(
                contactsMap.get(checkNotNull(email, "--email is required when --mode=UPDATE")),
                "No contact with the given email: %s",
                email);
        RegistrarContact newContact = updateContact(oldContact, registrar);
        stageEntityChange(oldContact, newContact);
        break;
      case DELETE:
        oldContact =
            checkNotNull(
                contactsMap.get(checkNotNull(email, "--email is required when --mode=DELETE")),
                "No contact with the given email: %s",
                email);
        stageEntityChange(oldContact, null);
        break;
      default:
        throw new AssertionError();
    }
    if (mode == Mode.CREATE || mode == Mode.UPDATE || mode == Mode.DELETE) {
      stageEntityChange(registrar, registrar.asBuilder().setContactsRequireSyncing(true).build());
    }
  }

  private void listContacts(Set<RegistrarContact> contacts) throws IOException {
    List<String> result = new ArrayList<>();
    for (RegistrarContact c : contacts) {
      result.add(c.toStringMultilinePlainText());
    }
    Files.write(output, Joiner.on('\n').join(result).getBytes(UTF_8));
  }

  private RegistrarContact createContact(Registrar registrar) {
    checkArgument(!isNullOrEmpty(name), "--name is required when --mode=CREATE");
    checkArgument(!isNullOrEmpty(email), "--email is required when --mode=CREATE");
    Builder builder = new RegistrarContact.Builder();
    builder.setParent(registrar);
    builder.setName(name);
    builder.setEmailAddress(email);
    if (phone != null) {
      builder.setPhoneNumber(phone.orNull());
    }
    if (fax != null) {
      builder.setFaxNumber(fax.orNull());
    }
    builder.setTypes(contactTypes);

    if (Objects.equals(allowConsoleAccess, Boolean.TRUE)) {
      builder.setGaeUserId(checkArgumentNotNull(
          GaeUserIdConverter.convertEmailAddressToGaeUserId(email),
          String.format("Email address %s is not associated with any GAE ID", email)));
    }
    if (visibleInWhoisAsAdmin != null) {
      builder.setVisibleInWhoisAsAdmin(visibleInWhoisAsAdmin);
    }
    if (visibleInWhoisAsTech != null) {
      builder.setVisibleInWhoisAsTech(visibleInWhoisAsTech);
    }
    return builder.build();
  }

  private RegistrarContact updateContact(RegistrarContact contact, Registrar registrar) {
    checkNotNull(registrar);
    checkNotNull(email, "--email is required when --mode=UPDATE");
    Builder builder = contact.asBuilder();
    builder.setParent(registrar);
    if (!isNullOrEmpty(name)) {
      builder.setName(name);
    }
    if (!isNullOrEmpty(email)) {
      builder.setEmailAddress(email);
    }
    if (phone != null) {
      builder.setPhoneNumber(phone.orNull());
    }
    if (fax != null) {
      builder.setFaxNumber(fax.orNull());
    }
    if (contactTypes != null) {
      builder.setTypes(contactTypes);
    }
    if (visibleInWhoisAsAdmin != null) {
      builder.setVisibleInWhoisAsAdmin(visibleInWhoisAsAdmin);
    }
    if (visibleInWhoisAsTech != null) {
      builder.setVisibleInWhoisAsTech(visibleInWhoisAsTech);
    }
    if (allowConsoleAccess != null) {
      if (allowConsoleAccess.equals(Boolean.TRUE)) {
        builder.setGaeUserId(checkArgumentNotNull(
            GaeUserIdConverter.convertEmailAddressToGaeUserId(email),
            String.format("Email address %s is not associated with any GAE ID", email)));
      } else {
        builder.setGaeUserId(null);
      }
    }
    return builder.build();
  }
}
