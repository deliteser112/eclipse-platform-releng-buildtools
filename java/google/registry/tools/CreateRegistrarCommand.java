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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Lists.newArrayList;
import static google.registry.model.registrar.Registrar.State.ACTIVE;
import static google.registry.tools.RegistryToolEnvironment.PRODUCTION;
import static google.registry.tools.RegistryToolEnvironment.SANDBOX;
import static google.registry.tools.RegistryToolEnvironment.UNITTEST;
import static google.registry.util.RegistrarUtils.normalizeClientId;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.Registrar;
import java.util.List;
import javax.annotation.Nullable;

/** Command to create a Registrar. */
@Parameters(separators = " =", commandDescription = "Create new registrar account(s)")
final class CreateRegistrarCommand extends CreateOrUpdateRegistrarCommand
    implements ServerSideCommand {

  private static final ImmutableSet<RegistryToolEnvironment> ENVIRONMENTS_ALLOWING_GROUP_CREATION =
      ImmutableSet.of(PRODUCTION, SANDBOX, UNITTEST);

  // Allows test cases to be cleaner.
  @VisibleForTesting
  static boolean requireAddress = true;

  @Parameter(
      names = "--create_groups",
      description = "Whether the Google Groups for this registrar should be created",
      arity = 1)
  boolean createGoogleGroups = true;

  private Connection connection;

  @Override
  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  @Override
  protected void initRegistrarCommand() throws Exception {
    checkArgument(mainParameters.size() == 1, "Must specify exactly one client identifier.");
    checkNotNull(emptyToNull(password), "--password is a required field");
    checkNotNull(registrarName, "--name is a required field");
    checkNotNull(icannReferralEmail, "--icann_referral_email is a required field");
    if (requireAddress) {
      checkNotNull(street, "Address fields are required when creating a registrar");
    }
    // Default new registrars to active.
    registrarState = Optional.fromNullable(registrarState).or(ACTIVE);
  }

  @Nullable
  @Override
  Registrar getOldRegistrar(final String clientId) {
    checkArgument(clientId.length() >= 3, "Client identifier (%s) is too short", clientId);
    checkArgument(clientId.length() <= 16, "Client identifier (%s) is too long", clientId);
    if (Registrar.Type.REAL.equals(registrarType)) {
      checkArgument(
          clientId.equals(normalizeClientId(clientId)),
          "Client identifier (%s) can only contain lowercase letters, numbers, and hyphens",
          clientId);
    }
    checkState(Registrar.loadByClientId(clientId) == null, "Registrar %s already exists", clientId);
    List<Registrar> collisions =
        newArrayList(filter(Registrar.loadAll(), new Predicate<Registrar>() {
          @Override
          public boolean apply(Registrar registrar) {
            return normalizeClientId(registrar.getClientId()).equals(clientId);
          }}));
    if (!collisions.isEmpty()) {
      throw new IllegalArgumentException(String.format(
          "The registrar client identifier %s normalizes identically to existing registrar %s",
          clientId,
          collisions.get(0).getClientId()));
    }
    return null;
  }

  @Override
  protected String postExecute() throws Exception {
    if (!createGoogleGroups) {
      return "";
    }
    // Allow prod and sandbox because they actually have Groups, and UNITTEST for testing.
    if (!ENVIRONMENTS_ALLOWING_GROUP_CREATION.contains(RegistryToolEnvironment.get())) {
      return "\nSkipping registrar groups creation because only production and sandbox support it.";
    }
    try {
      // We know it is safe to use the only main parameter here because initRegistrarCommand has
      // already verified that there is only one, and getOldRegistrar has already verified that a
      // registrar with this clientId doesn't already exist.
      CreateRegistrarGroupsCommand.executeOnServer(connection, getOnlyElement(mainParameters));
    } catch (Exception e) {
      return "\nRegistrar created, but groups creation failed with error:\n" + e;
    }
    return "\nRegistrar groups created successfully.";
  }
}
