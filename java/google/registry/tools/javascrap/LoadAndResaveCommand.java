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

import static com.google.common.collect.Maps.uniqueIndex;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.tools.MutatingCommand;
import java.util.Arrays;
import java.util.Map;
import org.joda.time.DateTime;

/** Load and resave an object in the probers, to trigger @OnSave changes. */
@Parameters(
    separators = " =",
    commandDescription = "Load and resave an object, to trigger @OnSave changes")
public final class LoadAndResaveCommand extends MutatingCommand {

  @Parameter(
    names = "--type",
    description =
        "Resource type (ContactResource, DomainApplication, DomainResource, HostResource).")
  protected String type;

  @Parameter(
    names = "--key",
    description = "Foreign key of the resource. ")
  protected String foreignKey;

  private static final Map<String, Class<? extends EppResource>> CLASSES_BY_NAME =
      uniqueIndex(
          Arrays.<Class<? extends EppResource>>asList(
              ContactResource.class,
              DomainApplication.class,
              DomainResource.class,
              HostResource.class),
          new Function<Class<?>, String>(){
            @Override
            public String apply(Class<?> clazz) {
              return clazz.getSimpleName();
            }});

  @Override
  protected void init() throws Exception {
    // Find the resource by foreign key, and then reload it directly, bypassing loadByUniqueId().
    // We need to do a reload because otherwise stageEntityChange() can fail due to the implicit
    // changes done when forwarding the resource to "now" in cloneProjectedAtTime().
    EppResource resource = ofy().load().entity(
        loadByUniqueId(CLASSES_BY_NAME.get(type), foreignKey, DateTime.now(UTC))).now();
    stageEntityChange(resource, resource);
  }
}

