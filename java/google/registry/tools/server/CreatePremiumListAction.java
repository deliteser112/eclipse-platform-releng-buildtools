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

package google.registry.tools.server;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.request.Action.Method.POST;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import google.registry.model.registry.label.PremiumList;
import google.registry.request.Action;
import google.registry.request.Parameter;
import java.util.List;
import javax.inject.Inject;

/**
 * An action that creates a premium list, for use by the {@code nomulus create_premium_list}
 * command.
 */
@Action(path = CreatePremiumListAction.PATH, method = POST)
public class CreatePremiumListAction extends CreateOrUpdatePremiumListAction {

  public static final String OVERRIDE_PARAM = "override";
  public static final String PATH = "/_dr/admin/createPremiumList";

  @Inject @Parameter(OVERRIDE_PARAM) boolean override;
  @Inject CreatePremiumListAction() {}

  @Override
  protected void savePremiumList() {
    checkArgument(
        !PremiumList.exists(name),
        "A premium list of this name already exists: %s.", name);
    if (!override) {
      assertTldExists(name);
    }

    logger.infofmt("Saving premium list for TLD %s", name);
    logger.infofmt("Got the following input data: %s", inputData);
    List<String> inputDataPreProcessed =
        Splitter.on('\n').omitEmptyStrings().splitToList(inputData);
    PremiumList premiumList = new PremiumList.Builder()
        .setName(name)
        .setPremiumListMapFromLines(inputDataPreProcessed)
        .build();
    premiumList.saveAndUpdateEntries();

    logger.infofmt("Saved premium list %s with entries %s",
        premiumList.getName(),
        premiumList.getPremiumListEntries());

    response.setPayload(ImmutableMap.of("status", "success"));
  }
}
