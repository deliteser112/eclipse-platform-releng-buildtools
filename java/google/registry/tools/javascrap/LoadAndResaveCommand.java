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

package google.registry.tools.javascrap;

import static google.registry.model.index.ForeignKeyIndex.loadAndGetKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.googlecode.objectify.Key;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.tools.MutatingCommand;
import org.joda.time.DateTime;

/** A command to load and resave an entity, which triggers @OnSave changes. */
@Parameters(
    separators = " =",
    commandDescription = "Load and resave an object, to trigger @OnSave changes")
public final class LoadAndResaveCommand extends MutatingCommand {

  private enum ResourceType { CONTACT, HOST, DOMAIN, APPLICATION }

  @Parameter(
    names = "--type",
    description = "Resource type.")
  protected ResourceType type;

  @Parameter(
    names = "--id",
    description = "Foreign key of the resource, or application ID of the domain application.")
  protected String uniqueId;

  @Override
  protected void init() throws Exception {
    Key<? extends EppResource> resourceKey = checkArgumentNotNull(
        getResourceKey(type, uniqueId, DateTime.now(UTC)),
        "Could not find active resource of type %s: %s", type, uniqueId);
    // Load the resource directly to bypass running cloneProjectedAtTime() automatically, which can
    // cause stageEntityChange() to fail due to implicit projection changes.
    EppResource resource = ofy().load().key(resourceKey).now();
    stageEntityChange(resource, resource);
  }

  private Key<? extends EppResource> getResourceKey(
      ResourceType type, String uniqueId, DateTime now) {
    switch (type) {
      case CONTACT:
        return loadAndGetKey(ContactResource.class, uniqueId, now);
      case HOST:
        return loadAndGetKey(HostResource.class, uniqueId, now);
      case DOMAIN:
        return loadAndGetKey(DomainResource.class, uniqueId, now);
      case APPLICATION:
        return Key.create(DomainApplication.class, uniqueId);
      default:
        throw new IllegalStateException("Unknown type: " + type);
    }
  }
}
