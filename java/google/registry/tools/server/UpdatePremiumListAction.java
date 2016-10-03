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

package google.registry.tools.server;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.request.Action.Method.POST;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import google.registry.model.registry.label.PremiumList;
import google.registry.request.Action;
import java.util.List;
import javax.inject.Inject;

/**
 * An action that creates a premium list, for use by the {@code nomulus create_premium_list}
 * command.
 */
@Action(path = UpdatePremiumListAction.PATH, method = POST)
public class UpdatePremiumListAction extends CreateOrUpdatePremiumListAction {

  public static final String PATH = "/_dr/admin/updatePremiumList";

  @Inject UpdatePremiumListAction() {}

  @Override
  protected void savePremiumList() {
    Optional<PremiumList> existingName = PremiumList.get(name);
    checkArgument(
        existingName.isPresent(),
        "Could not update premium list %s because it doesn't exist.",
        name);

    logger.infofmt("Updating premium list for TLD %s", name);
    logger.infofmt("Got the following input data: %s", inputData);
    List<String> inputDataPreProcessed =
        Splitter.on('\n').omitEmptyStrings().splitToList(inputData);
    PremiumList premiumList = existingName.get().asBuilder()
        .setPremiumListMapFromLines(inputDataPreProcessed)
        .build();
    premiumList.saveAndUpdateEntries();

    logger.infofmt("Updated premium list %s with entries %s",
        premiumList.getName(),
        premiumList.getPremiumListEntries());

    String message = String.format(
        "Saved premium list %s with %d entries.\n",
        premiumList.getName(),
        premiumList.getPremiumListEntries().size());
    response.setPayload(ImmutableMap.of(
        "status", "success",
        "message", message));
  }
}
