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

package com.google.domain.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.domain.registry.model.registrar.Registrar.State.ACTIVE;
import static com.google.domain.registry.util.RegistrarUtils.normalizeClientId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.tools.Command.GtechCommand;

import com.beust.jcommander.Parameters;

import java.util.List;

import javax.annotation.Nullable;

/** Command to create a Registrar. */
@Parameters(separators = " =", commandDescription = "Create new registrar account(s)")
final class CreateRegistrarCommand extends CreateOrUpdateRegistrarCommand implements GtechCommand {

  // Allows test cases to be cleaner.
  @VisibleForTesting
  static boolean requireAddress = true;

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
  Registrar getOldRegistrar(final String clientIdentifier) {
    checkArgument(clientIdentifier.length() >= 3,
        String.format("Client identifier (%s) is too short", clientIdentifier));
    checkArgument(clientIdentifier.length() <= 16,
        String.format("Client identifier (%s) is too long", clientIdentifier));
    if (Registrar.Type.REAL.equals(registrarType)) {
      checkArgument(clientIdentifier.equals(normalizeClientId(clientIdentifier)),
          String.format(
              "Client identifier (%s) can only contain lowercase letters, numbers, and hyphens",
              clientIdentifier));
    }
    checkState(Registrar.loadByClientId(clientIdentifier) == null,
        "Registrar %s already exists", clientIdentifier);
    List<Registrar> collisions =
        newArrayList(filter(Registrar.loadAll(), new Predicate<Registrar>() {
          @Override
          public boolean apply(Registrar registrar) {
            return normalizeClientId(registrar.getClientIdentifier()).equals(clientIdentifier);
          }}));
    if (!collisions.isEmpty()) {
      throw new IllegalArgumentException(String.format(
          "The registrar client identifier %s normalizes identically to existing registrar %s",
          clientIdentifier,
          collisions.get(0).getClientIdentifier()));
    }
    return null;
  }
}
