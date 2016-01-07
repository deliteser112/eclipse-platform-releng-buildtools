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

package google.registry.flows.domain;

import google.registry.flows.EppException;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.fee.BaseFee;
import google.registry.model.eppinput.EppInput;
import google.registry.model.reporting.HistoryEntry;
import java.util.Set;
import org.joda.time.DateTime;

/**
 * Interface for classes which provide extra registry logic for things like TLD-specific rules and
 * discounts.
 */
public interface RegistryExtraFlowLogic {

  /**
   * Gets the flags to be used in the EPP flags extension.
   *
   * <p>This is used for EPP info commands.
   */
  public Set<String> getExtensionFlags(
      DomainResource domainResource, String clientId, DateTime asOfDate);

  /**
   * Computes the expected creation fee.
   *
   * <p>For use in fee challenges and the like.
   */
  public BaseFee getCreateFeeOrCredit(
      String domainName,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for a create command.
   *
   * <p>Any changes should not be persisted to Datastore until commitAdditionalLogicChanges is
   * called.
   */
  public void performAdditionalDomainCreateLogic(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException;

  /**
   * Performs additional tasks required for a delete command.
   *
   * <p>Any changes should not be persisted to Datastore until commitAdditionalLogicChanges is
   * called.
   */
  public void performAdditionalDomainDeleteLogic(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException;

  /**
   * Computes the expected renewal fee.
   *
   * <p>For use in fee challenges and the like.
   */
  public BaseFee getRenewFeeOrCredit(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for a renew command.
   *
   * <p>Any changes should not be persisted to Datastore until commitAdditionalLogicChanges is
   * called.
   */
  public void performAdditionalDomainRenewLogic(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException;

  /**
   * Performs additional tasks required for a restore command.
   *
   * <p>Any changes should not be persisted to Datastore until commitAdditionalLogicChanges is
   * called.
   */
  public void performAdditionalDomainRestoreLogic(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException;

  /**
   * Performs additional tasks required for a transfer command.
   *
   * <p>Any changes should not be persisted to Datastore until commitAdditionalLogicChanges is
   * called.
   */
  public void performAdditionalDomainTransferLogic(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException;

  /**
   * Computes the expected update fee.
   *
   * <p>For use in fee challenges and the like.
   */
  public BaseFee getUpdateFeeOrCredit(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      EppInput eppInput) throws EppException;

  /**
   * Performs additional tasks required for an update command.
   *
   * <p>Any changes should not be persisted to Datastore until commitAdditionalLogicChanges is
   * called.
   */
  public void performAdditionalDomainUpdateLogic(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException;

  /** Commits any changes made as a result of a call to one of the performXXX methods. */
  public void commitAdditionalLogicChanges();
}
