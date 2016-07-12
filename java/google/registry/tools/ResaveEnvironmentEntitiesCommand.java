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

package google.registry.tools;

import static com.google.common.collect.Iterables.concat;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameters;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import google.registry.model.ImmutableObject;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.registry.Registry;
import google.registry.tools.Command.RemoteApiCommand;

/**
 * Command to re-save all environment entities to ensure that they have valid commit logs.
 *
 * <p>The entities that are re-saved are those of type {@link Registry}, {@link Registrar}, and
 * {@link RegistrarContact}.
 */
@Parameters(commandDescription = "Re-save all environment entities.")
final class ResaveEnvironmentEntitiesCommand implements RemoteApiCommand {

  @Override
  public void run() throws Exception {
    Iterable<Key<? extends ImmutableObject>> keys = concat(
        ofy().load().type(Registrar.class).ancestor(getCrossTldKey()).keys(),
        ofy().load().type(Registry.class).ancestor(getCrossTldKey()).keys(),
        ofy().load().type(RegistrarContact.class).ancestor(getCrossTldKey()).keys());
    for (final Key<? extends ImmutableObject> key : keys) {
      ofy().transact(new VoidWork() {
          @Override
          public void vrun() {
            ofy().save().entity(ofy().load().key(key).now());
          }});
      System.out.printf("Re-saved entity %s\n", key);
    }
  }
}
