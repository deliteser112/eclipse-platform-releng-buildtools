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

package com.google.domain.registry.rdap;

import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.request.Action.Method.GET;
import static com.google.domain.registry.request.Action.Method.HEAD;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DesignatedContact;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.HttpException.BadRequestException;
import com.google.domain.registry.request.HttpException.NotFoundException;
import com.google.domain.registry.util.Clock;

import com.googlecode.objectify.Key;

import java.util.regex.Pattern;

import javax.inject.Inject;

/**
 * RDAP (new WHOIS) action for entity (contact and registrar) requests.
 */
@Action(path = RdapEntityAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapEntityAction extends RdapActionBase {

  public static final String PATH = "/rdap/entity/";

  private static final Pattern ROID_PATTERN = Pattern.compile("[-.a-zA-Z0-9]+");

  @Inject Clock clock;
  @Inject RdapEntityAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "entity";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(String pathSearchString) {
    // The query string is not used; the RDAP syntax is /rdap/entity/handle (the handle is the roid
    // for contacts and the client identifier for registrars). Since RDAP's concept of an entity
    // includes both contacts and registrars, search for one first, then the other.
    boolean wasValidKey = false;
    if (ROID_PATTERN.matcher(pathSearchString).matches()) {
      wasValidKey = true;
      Key<ContactResource> contactKey = Key.create(ContactResource.class, pathSearchString);
      ContactResource contactResource = ofy().load().key(contactKey).now();
      if ((contactResource != null) && clock.nowUtc().isBefore(contactResource.getDeletionTime())) {
        return RdapJsonFormatter.makeRdapJsonForContact(
            contactResource,
            Optional.<DesignatedContact.Type>absent(),
            rdapLinkBase,
            rdapWhoisServer);
      }
    }
    String clientId = pathSearchString.trim();
    if ((clientId.length() >= 3) && (clientId.length() <= 16)) {
      wasValidKey = true;
      Registrar registrar = Registrar.loadByClientId(clientId);
      if ((registrar != null) && registrar.isActiveAndPubliclyVisible()) {
        return RdapJsonFormatter.makeRdapJsonForRegistrar(registrar, rdapLinkBase, rdapWhoisServer);
      }
    }
    throw !wasValidKey
        ? new BadRequestException(pathSearchString + " is not a valid entity handle")
        : new NotFoundException(pathSearchString + " not found");
  }
}
