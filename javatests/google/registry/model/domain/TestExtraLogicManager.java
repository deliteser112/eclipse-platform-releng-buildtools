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

package google.registry.model.domain;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import google.registry.flows.EppException;
import google.registry.flows.EppException.UnimplementedExtensionException;
import google.registry.flows.domain.RegistryExtraFlowLogic;
import google.registry.model.domain.flags.FlagsUpdateCommandExtension;
import google.registry.model.eppinput.EppInput;
import java.util.List;
import org.joda.time.DateTime;

/**
 * Fake extra logic manager which synthesizes information from the domain name for testing purposes.
 */
public class TestExtraLogicManager implements RegistryExtraFlowLogic {

  @Override
  public List<String> getExtensionFlags(
      DomainResource domainResource, String clientIdentifier, DateTime asOfDate) {
    // Take the part before the period, split by dashes, and treat each part after the first as
    // a flag.
    List<String> components =
        Splitter.on('-').splitToList(
            Iterables.getFirst(
                Splitter.on('.').split(domainResource.getFullyQualifiedDomainName()), ""));
    return components.subList(1, components.size());
  }

  @Override
  public void performAdditionalDomainUpdateLogic(
      DomainResource domainResource,
      String clientIdentifier,
      DateTime asOfDate,
      EppInput eppInput) throws EppException {
    FlagsUpdateCommandExtension updateFlags =
        eppInput.getSingleExtension(FlagsUpdateCommandExtension.class);
    if (updateFlags == null) {
      return;
    }
    // Throw this exception as a signal to the test that we got this far.
    throw new UnimplementedExtensionException();
  }

  @Override
  public void commitAdditionalDomainUpdates() {
    return;
  }
}
