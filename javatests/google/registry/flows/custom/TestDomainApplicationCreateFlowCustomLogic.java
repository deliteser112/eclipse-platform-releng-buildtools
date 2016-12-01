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

package google.registry.flows.custom;

import com.google.common.base.Joiner;
import com.google.common.net.InternetDomainName;
import google.registry.flows.SessionMetadata;
import google.registry.flows.domain.DomainApplicationCreateFlow;
import google.registry.model.domain.flags.FlagsCreateCommandExtension;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppoutput.CreateData.DomainCreateData;

/** A class to customize {@link DomainApplicationCreateFlow} for testing. */
public class TestDomainApplicationCreateFlowCustomLogic
    extends DomainApplicationCreateFlowCustomLogic {

  protected TestDomainApplicationCreateFlowCustomLogic(
      EppInput eppInput, SessionMetadata sessionMetadata) {
    super(eppInput, sessionMetadata);
  }

  private String getTld() {
    return InternetDomainName.from(getEppInput().getTargetIds().get(0)).parent().toString();
  }

  @Override
  public BeforeResponseReturnData beforeResponse(BeforeResponseParameters parameters) {
    if (getTld().equals("flags")) {
      String flagsPrefix =
          Joiner.on('-')
              .join(getEppInput().getSingleExtension(FlagsCreateCommandExtension.class).getFlags());

      DomainCreateData resData = (DomainCreateData) parameters.resData();
      resData =
          DomainCreateData.create(
              Joiner.on('-').join(flagsPrefix, resData.name()),
              resData.creationDate(),
              resData.expirationDate());

      return BeforeResponseReturnData.newBuilder()
          .setResData(resData)
          .setResponseExtensions(parameters.responseExtensions())
          .build();
    } else {
      return BeforeResponseReturnData.newBuilder()
          .setResData(parameters.resData())
          .setResponseExtensions(parameters.responseExtensions())
          .build();
    }
  }
}
