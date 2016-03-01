// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.rdap;

import static com.google.domain.registry.request.Action.Method.GET;
import static com.google.domain.registry.request.Action.Method.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.rdap.RdapJsonFormatter.MakeRdapJsonNoticeParameters;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.HttpException;
import com.google.domain.registry.request.HttpException.InternalServerErrorException;
import com.google.domain.registry.request.HttpException.NotFoundException;
import com.google.domain.registry.util.Clock;

import javax.inject.Inject;

/**
 * RDAP (new WHOIS) action for help requests.
 */
@Action(path = RdapHelpAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapHelpAction extends RdapActionBase {

  public static final String PATH = "/rdap/help";

  private static final ImmutableMap<String, MakeRdapJsonNoticeParameters> HELP_MAP =
      ImmutableMap.of(
          "/",
          MakeRdapJsonNoticeParameters.builder()
              .title("RDAP Help")
              .description(ImmutableList.of(
                  "RDAP Help Topics (use /help/topic for information)",
                  "syntax",
                  "tos (Terms of Service)"))
              .linkValueSuffix("help/")
              .linkHrefUrlString("https://www.registry.google/about/rdap/index.html")
              .build(),
          "/index",
          MakeRdapJsonNoticeParameters.builder()
              .title("RDAP Help")
              .description(ImmutableList.of(
                  "RDAP Help Topics (use /help/topic for information)",
                  "syntax",
                  "tos (Terms of Service)"))
              .linkValueSuffix("help/index")
              .linkHrefUrlString("https://www.registry.google/about/rdap/index.html")
              .build(),
          "/syntax",
          MakeRdapJsonNoticeParameters.builder()
              .title("RDAP Command Syntax")
              .description(ImmutableList.of(
                  "domain/XXXX",
                  "nameserver/XXXX",
                  "entity/XXXX",
                  "domains?name=XXXX",
                  "domains?nsLdhName=XXXX",
                  "domains?nsIp=XXXX",
                  "nameservers?name=XXXX",
                  "nameservers?ip=XXXX",
                  "entities?fn=XXXX",
                  "entities?handle=XXXX",
                  "help/XXXX"))
              .linkValueSuffix("help/syntax")
              .linkHrefUrlString("https://www.registry.google/about/rdap/syntax.html")
              .build(),
          "/tos",
          MakeRdapJsonNoticeParameters.builder()
              .title("RDAP Terms of Service")
              .description(ImmutableList.of(
                  "By querying our Domain Database, you are agreeing to comply with these terms so"
                      + " please read them carefully.",
                  "Any information provided is 'as is' without any guarantee of accuracy.",
                  "Please do not misuse the Domain Database. It is intended solely for"
                      + " query-based access.",
                  "Don't use the Domain Database to allow, enable, or otherwise support the"
                      + " transmission of mass unsolicited, commercial advertising or"
                      + " solicitations.",
                  "Don't access our Domain Database through the use of high volume, automated"
                      + " electronic processes that send queries or data to the systems of"
                      + " Charleston Road Registry or any ICANN-accredited registrar.",
                  "You may only use the information contained in the Domain Database for lawful"
                      + " purposes.",
                  "Do not compile, repackage, disseminate, or otherwise use the information"
                      + " contained in the Domain Database in its entirety, or in any substantial"
                      + " portion, without our prior written permission.",
                  "We may retain certain details about queries to our Domain Database for the"
                      + " purposes of detecting and preventing misuse.",
                  "We reserve the right to restrict or deny your access to the database if we"
                      + " suspect that you have failed to comply with these terms.",
                  "We reserve the right to modify this agreement at any time."))
              .linkValueSuffix("help/tos")
              .linkHrefUrlString("https://www.registry.google/about/rdap/tos.html")
              .build());

  @Inject Clock clock;
  @Inject RdapHelpAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "help";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(String pathSearchString)
      throws HttpException {
    if (pathSearchString.isEmpty()) {
      pathSearchString = "/";
    }
    if (!HELP_MAP.containsKey(pathSearchString)) {
      throw new NotFoundException("no help found for " + pathSearchString);
    }
    try {
      return ImmutableMap.of(
          "notices",
          (Object) ImmutableList.of(RdapJsonFormatter.makeRdapJsonNotice(
              HELP_MAP.get(pathSearchString), rdapLinkBase)));
    } catch (Exception e) {
      throw new InternalServerErrorException("unable to read help for " + pathSearchString);
    }
  }
}
