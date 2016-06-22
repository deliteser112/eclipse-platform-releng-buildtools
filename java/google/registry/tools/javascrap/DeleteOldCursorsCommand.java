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

import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameters;

import google.registry.model.common.EntityGroupRoot;
import google.registry.model.registry.RegistryCursor;
import google.registry.tools.MutatingCommand;

/** Deletes old {@link RegistryCursor} entities. */
@Parameters(
    separators = " =",
    commandDescription = "Delete old RegistryCursor entities")
public final class DeleteOldCursorsCommand extends MutatingCommand {

  @Override
  protected void init() throws Exception {
    for (RegistryCursor cursor :
        ofy().load().type(RegistryCursor.class).ancestor(EntityGroupRoot.getCrossTldKey())) {
      stageEntityChange(cursor, null);
    }
  }
}
