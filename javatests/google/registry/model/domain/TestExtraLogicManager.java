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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import google.registry.flows.EppException;
import google.registry.flows.domain.RegistryExtraFlowLogic;
import google.registry.model.domain.fee.BaseFee;
import google.registry.model.domain.fee.BaseFee.FeeType;
import google.registry.model.domain.fee.Credit;
import google.registry.model.domain.fee.Fee;
import google.registry.model.domain.flags.FlagsCreateCommandExtension;
import google.registry.model.domain.flags.FlagsTransferCommandExtension;
import google.registry.model.domain.flags.FlagsUpdateCommandExtension;
import google.registry.model.eppinput.EppInput;
import google.registry.model.reporting.HistoryEntry;
import java.math.BigDecimal;
import java.util.List;
import org.joda.time.DateTime;

/**
 * Fake extra logic manager which synthesizes information from the domain name for testing purposes.
 */
public class TestExtraLogicManager implements RegistryExtraFlowLogic {

  private String messageToThrow = null;

  /**
   * Dummy exception used to signal success. This is thrown by the commitAdditionalLogicChanges
   * method to indicate to the test that everything worked properly, because the
   * TestExtraLogicManager instance used by the flow will have been created deep in the flow and is
   * not accessible to the test code directly.  We should fix this when we make the extra flow logic
   * injected.
   */
  public static class TestExtraLogicManagerSuccessException extends RuntimeException {
    TestExtraLogicManagerSuccessException(String message) {
      super(message);
    }
  }

  @Override
  public List<String> getExtensionFlags(
      DomainResource domainResource, String clientId, DateTime asOfDate) {
    // Take the part before the period, split by dashes, and treat each part after the first as
    // a flag.
    List<String> components =
        Splitter.on('-').splitToList(
            Iterables.getFirst(
                Splitter.on('.').split(domainResource.getFullyQualifiedDomainName()), ""));
    return components.subList(1, components.size());
  }

  BaseFee domainNameToFeeOrCredit(String domainName) {
    // The second-level domain should be of the form "description-price", where description is the
    // description string of the fee or credit, and price is the price (credit if negative, fee
    // otherwise). To make sure this is a valid domain name, don't use any spaces, and limit prices
    // to integers. Don't use a two-character description for credits, since it is illegal to have
    // both the third and fourth characters of a domain name label be hyphens.
    List<String> components =
        Splitter.on('-').limit(2).splitToList(
            Iterables.getFirst(Splitter.on('.').split(domainName), ""));
    checkArgument(components.size() == 2, "Domain name must be of the form description-price.tld");
    int price = Integer.parseInt(components.get(1));
    if (price < 0) {
      return Credit.create(
          new BigDecimal(price), FeeType.valueOf(Ascii.toUpperCase(components.get(0))));
    } else {
      return Fee.create(
          new BigDecimal(price), FeeType.valueOf(Ascii.toUpperCase(components.get(0))));
    }
  }

  /** Computes the expected create cost, for use in fee challenges and the like. */
  @Override
  public BaseFee getCreateFeeOrCredit(
      String domainName,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException {
    return domainNameToFeeOrCredit(domainName);
  }

  /**
   * Performs additional tasks required for a create command. Any changes should not be persisted to
   * Datastore until commitAdditionalLogicChanges is called.
   */
  @Override
  public void performAdditionalDomainCreateLogic(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException {
    FlagsCreateCommandExtension flags =
        eppInput.getSingleExtension(FlagsCreateCommandExtension.class);
    if (flags == null) {
      return;
    }
    messageToThrow = Joiner.on(',').join(flags.getFlags());
  }

  /**
   * Performs additional tasks required for a delete command. Any changes should not be persisted to
   * Datastore until commitAdditionalLogicChanges is called.
   */
  @Override
  public void performAdditionalDomainDeleteLogic(
      DomainResource domainResource,
      String clientId,
      DateTime asOfDate,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException {
    messageToThrow = "deleted";
  }

  /** Computes the expected renewal cost, for use in fee challenges and the like. */
  @Override
  public BaseFee getRenewFeeOrCredit(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput) throws EppException {
    return domainNameToFeeOrCredit(domain.getFullyQualifiedDomainName());
  }

  /**
   * Performs additional tasks required for a renew command. Any changes should not be persisted
   * to Datastore until commitAdditionalLogicChanges is called.
   */
  @Override
  public void performAdditionalDomainRenewLogic(
      DomainResource domainResource,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException {
    messageToThrow = "renewed";
  }

  /**
   * Performs additional tasks required for a restore command. Any changes should not be persisted
   * to Datastore until commitAdditionalLogicChanges is called.
   */
  @Override
  public void performAdditionalDomainRestoreLogic(
      DomainResource domainResource,
      String clientId,
      DateTime asOfDate,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException {
    messageToThrow = "restored";
  }

  /**
   * Performs additional tasks required for a transfer command. Any changes should not be persisted
   * to Datastore until commitAdditionalLogicChanges is called.
   */
  @Override
  public void performAdditionalDomainTransferLogic(
      DomainResource domainResource,
      String clientId,
      DateTime asOfDate,
      int years,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException {
    FlagsTransferCommandExtension flags =
        eppInput.getSingleExtension(FlagsTransferCommandExtension.class);
    if (flags == null) {
      return;
    }
    messageToThrow =
        "add:"
        + Joiner.on(',').join(flags.getAddFlags().getFlags())
        + ";remove:"
        + Joiner.on(',').join(flags.getRemoveFlags().getFlags());
  }

  /** Computes the expected update cost, for use in fee challenges and the like. */
  @Override
  public BaseFee getUpdateFeeOrCredit(
      DomainResource domain,
      String clientId,
      DateTime asOfDate,
      EppInput eppInput) throws EppException {
    return domainNameToFeeOrCredit(domain.getFullyQualifiedDomainName());
  }

  /**
   * Performs additional tasks required for an update command. Any changes should not be persisted
   * to Datastore until commitAdditionalLogicChanges is called.
   */
  @Override
  public void performAdditionalDomainUpdateLogic(
      DomainResource domainResource,
      String clientId,
      DateTime asOfDate,
      EppInput eppInput,
      HistoryEntry historyEntry) throws EppException {
    FlagsUpdateCommandExtension flags =
        eppInput.getSingleExtension(FlagsUpdateCommandExtension.class);
    if (flags == null) {
      return;
    }
    messageToThrow =
        "add:"
        + Joiner.on(',').join(flags.getAddFlags().getFlags())
        + ";remove:"
        + Joiner.on(',').join(flags.getRemoveFlags().getFlags());
  }

  @Override
  public void commitAdditionalLogicChanges() {
    checkNotNull(messageToThrow);
    // Throw a specific exception as a signal to the test code that we made it through to here.
    throw new TestExtraLogicManagerSuccessException(messageToThrow);
  }
}
