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

package com.google.domain.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.domain.registry.model.registry.Registries.assertTldExists;
import static com.google.domain.registry.util.CollectionUtils.nullToEmpty;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.registry.Registry;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.util.List;

import javax.annotation.Nullable;

/** Command to update a TLD. */
@Parameters(separators = " =", commandDescription = "Update existing TLD(s)")
class UpdateTldCommand extends CreateOrUpdateTldCommand {
  @Nullable
  @Parameter(
      names = "--add_reserved_lists",
      description = "A comma-separated list of reserved list names to be added to the TLD")
  List<String> reservedListsAdd;

  @Nullable
  @Parameter(
      names = "--remove_reserved_lists",
      description = "A comma-separated list of reserved list names to be removed from the TLD")
  List<String> reservedListsRemove;

  @Override
  Registry getOldRegistry(String tld) {
    return Registry.get(assertTldExists(tld));
  }

  @Override
  protected void initTldCommand() throws Exception {
    checkArgument(reservedListsAdd == null || reservedListNames == null,
        "Don't pass both --reserved_lists and --add_reserved_lists");
    reservedListNamesToAdd = ImmutableSet.copyOf(nullToEmpty(reservedListsAdd));
    checkArgument(reservedListsRemove == null || reservedListNames == null,
        "Don't pass both --reserved_lists and --remove_reserved_lists");
    reservedListNamesToRemove = ImmutableSet.copyOf(nullToEmpty(reservedListsRemove));
  }
}
