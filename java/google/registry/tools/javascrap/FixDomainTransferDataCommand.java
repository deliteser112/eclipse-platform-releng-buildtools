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

package google.registry.tools.javascrap;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import google.registry.model.domain.DomainResource;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferStatus;
import google.registry.tools.MutatingCommand;
import java.util.List;

/**
 * Scrap tool to fix domain transfer data corrupted in b/33289763.
 *
 * <p>A bug caused some domains to have their transfer data overwritten with SERVER_CANCELLED
 * resolved transfer data upon their deletion, regardless of the original transfer data. The
 * previous version of this tool fixed domains where their original transfer data was empty by just
 * removing the corrupted transfer data. However, one other domain was affected that did have
 * pre-existing transfer data; this domain was not fixed earlier, so this new version of the scrap
 * tool fixes it by restoring what the original server-approved transfer data.
 */
@Parameters(separators = " =", commandDescription = "Fix transfer data for b/33289763.")
public class FixDomainTransferDataCommand extends MutatingCommand {

  @Parameter(description = "Domain roids", required = true)
  private List<String> mainParameters;

  @Override
  protected void init() throws Exception {
    for (String domainRoid : mainParameters) {
      DomainResource domain = ofy().load().type(DomainResource.class).id(domainRoid).now();
      checkNotNull(domain);
      TransferData fixedTransferData =
          domain.getTransferData().asBuilder()
              .setTransferStatus(TransferStatus.SERVER_APPROVED)
              .setPendingTransferExpirationTime(domain.getLastTransferTime())
              .build();
      stageEntityChange(domain, domain.asBuilder().setTransferData(fixedTransferData).build());
    }
  }
}
