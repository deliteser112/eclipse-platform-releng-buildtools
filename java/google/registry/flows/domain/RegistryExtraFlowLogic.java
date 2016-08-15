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

package google.registry.flows.domain;

import google.registry.flows.EppException;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppinput.EppInput;
import java.util.List;
import org.joda.time.DateTime;

/**
 * Interface for classes which provide extra registry logic for things like TLD-specific rules and
 * discounts.
 */
public interface RegistryExtraFlowLogic {

  /** Get the flags to be used in the EPP flags extension. This is used for EPP info commands. */
  public List<String> getExtensionFlags(
      DomainResource domainResource, String clientIdentifier, DateTime asOfDate);

  /**
   * Add and remove flags passed via the EPP flags extension. Any changes should not be persisted to
   * Datastore until commitAdditionalDomainUpdates is called. Name suggested by Benjamin McIlwain.
   */
  public void performAdditionalDomainUpdateLogic(
      DomainResource domainResource,
      String clientIdentifier,
      DateTime asOfDate,
      EppInput eppInput) throws EppException;

  /** Commit any changes made as a result of a call to performAdditionalDomainUpdateLogic(). */
  public void commitAdditionalDomainUpdates();
}
