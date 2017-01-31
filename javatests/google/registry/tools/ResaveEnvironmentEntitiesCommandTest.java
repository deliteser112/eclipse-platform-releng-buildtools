// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;

import com.google.common.base.Function;
import google.registry.model.ImmutableObject;
import google.registry.model.ofy.CommitLogManifest;
import google.registry.model.ofy.CommitLogMutation;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.registry.Registry;
import org.junit.Test;

/** Unit tests for {@link ResaveEnvironmentEntitiesCommand}. */
public class ResaveEnvironmentEntitiesCommandTest
    extends CommandTestCase<ResaveEnvironmentEntitiesCommand> {

  @Test
  public void testSuccess_noop() throws Exception {
    // Get rid of all the entities that this command runs on so that it does nothing.
    deleteEntitiesOfTypes(
        Registry.class,
        Registrar.class,
        RegistrarContact.class,
        CommitLogManifest.class,
        CommitLogMutation.class);
    runCommand();
    assertThat(ofy().load().type(CommitLogManifest.class).keys()).isEmpty();
    assertThat(ofy().load().type(CommitLogMutation.class).keys()).isEmpty();
  }

  @Test
  public void testSuccess_createsCommitLogs() throws Exception {
    createTld("tld");
    deleteEntitiesOfTypes(CommitLogManifest.class, CommitLogMutation.class);
    assertThat(ofy().load().type(CommitLogManifest.class).keys()).isEmpty();
    assertThat(ofy().load().type(CommitLogMutation.class).keys()).isEmpty();
    runCommand();

    // There are five entities that have been re-saved at this point (each in a separate
    // transaction), so expect five manifests and five mutations.
    assertThat(ofy().load().type(CommitLogManifest.class).keys()).hasSize(5);
    Iterable<ImmutableObject> savedEntities =
        transform(
            ofy().load().type(CommitLogMutation.class).list(),
            new Function<CommitLogMutation, ImmutableObject>() {
              @Override
              public ImmutableObject apply(CommitLogMutation mutation) {
                return ofy().load().fromEntity(mutation.getEntity());
              }
            });
    assertThat(savedEntities)
        .containsExactly(
            // The Registrars and RegistrarContacts are created by AppEngineRule.
            Registrar.loadByClientId("TheRegistrar"),
            Registrar.loadByClientId("NewRegistrar"),
            Registry.get("tld"),
            getOnlyElement(Registrar.loadByClientId("TheRegistrar").getContacts()),
            getOnlyElement(Registrar.loadByClientId("NewRegistrar").getContacts()));
  }

  @SafeVarargs
  private static void deleteEntitiesOfTypes(Class<? extends ImmutableObject>... types) {
    for (Class<? extends ImmutableObject> type : types) {
      ofy().deleteWithoutBackup().keys(ofy().load().type(type).keys()).now();
    }
  }
}
