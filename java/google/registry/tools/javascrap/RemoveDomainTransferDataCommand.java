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

package google.registry.tools.javascrap;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import google.registry.model.domain.DomainResource;
import google.registry.model.transfer.TransferData;
import google.registry.tools.MutatingCommand;
import java.util.List;
import org.joda.time.DateTime;

/**
 * Scrap tool to remove domain transfer data.
 *
 * <p>A bug has caused some domains to have invalid transfer data; the status is SERVER_CANCELLED,
 * but other fields such as the gaining and losing registrar are blank. Since there was never an
 * actual transfer, the proper course of action is to remove the invalid data. See b/33289763.
 */
@Parameters(separators = " =", commandDescription = "Remove transfer data.")
public class RemoveDomainTransferDataCommand extends MutatingCommand {

  @Parameter(description = "Fully-qualified domain names", required = true)
  private List<String> mainParameters;

  @Override
  protected void init() throws Exception {
    DateTime now = DateTime.now(UTC);
    for (String domainName : mainParameters) {
      DomainResource domain = loadByForeignKey(DomainResource.class, domainName, now);
      checkNotNull(domain);
      stageEntityChange(domain, domain.asBuilder().setTransferData(TransferData.EMPTY).build());
    }
  }
}
