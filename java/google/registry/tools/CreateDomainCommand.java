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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.template.soy.data.SoyMapData;
import google.registry.tools.soy.DomainCreateSoyInfo;
import google.registry.util.StringGenerator;
import javax.inject.Inject;

/** A command to create a new domain via EPP. */
@Parameters(separators = " =", commandDescription = "Create a new domain via EPP.")
final class CreateDomainCommand extends CreateOrUpdateDomainCommand {

  @Parameter(
      names = "--period",
      description = "Initial registration period, in years.")
  private Integer period;

  @Inject
  StringGenerator passwordGenerator;

  private static final int PASSWORD_LENGTH = 16;

  @Override
  protected void initMutatingEppToolCommand() {
    checkArgumentNotNull(registrant, "Registrant must be specified");
    checkArgument(!admins.isEmpty(), "At least one admin must be specified");
    checkArgument(!techs.isEmpty(), "At least one tech must be specified");
    if (isNullOrEmpty(password)) {
      password = passwordGenerator.createString(PASSWORD_LENGTH);
    }

    for (String domain : domains) {
      setSoyTemplate(DomainCreateSoyInfo.getInstance(), DomainCreateSoyInfo.DOMAINCREATE);
      addSoyRecord(
          clientId,
          new SoyMapData(
              "domain", domain,
              "period", period == null ? null : period.toString(),
              "nameservers", nameservers,
              "registrant", registrant,
              "admins", admins,
              "techs", techs,
              "password", password,
              "dsRecords", DsRecord.convertToSoy(dsRecords)));
    }
  }
}
