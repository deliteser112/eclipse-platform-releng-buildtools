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

package google.registry.rdap;

import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
import google.registry.rdap.RdapJsonFormatter.MakeRdapJsonNoticeParameters;
import google.registry.request.Action;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.util.Clock;
import javax.inject.Inject;

/**
 * RDAP (new WHOIS) action for help requests.
 */
@Action(path = RdapHelpAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapHelpAction extends RdapActionBase {

  public static final String PATH = "/rdap/help";

  /**
   * Path for the terms of service. The terms of service are also used to create the required
   * boilerplate notice, so we make it a publicly visible that we can use elsewhere to reference it.
   */
  public static final String TERMS_OF_SERVICE_PATH = "/tos";

  /**
   * Map from a relative path underneath the RDAP root path to the appropriate
   * {@link MakeRdapJsonNoticeParameters} object.
   */
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
          TERMS_OF_SERVICE_PATH,
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
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest, String linkBase) {
    // We rely on addTopLevelEntries to notice if we are sending the TOS notice, and not add a
    // duplicate boilerplate entry.
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    RdapJsonFormatter.addTopLevelEntries(
        builder,
        BoilerplateType.OTHER,
        ImmutableList.of(getJsonHelpNotice(pathSearchString, rdapLinkBase)),
        ImmutableList.<ImmutableMap<String, Object>>of(),
        rdapLinkBase);
    return builder.build();
  }

  static ImmutableMap<String, Object> getJsonHelpNotice(
      String pathSearchString, String rdapLinkBase) {
    if (pathSearchString.isEmpty()) {
      pathSearchString = "/";
    }
    if (!HELP_MAP.containsKey(pathSearchString)) {
      throw new NotFoundException("no help found for " + pathSearchString);
    }
    try {
      return RdapJsonFormatter.makeRdapJsonNotice(
          HELP_MAP.get(pathSearchString), rdapLinkBase);
    } catch (Exception e) {
      throw new InternalServerErrorException("unable to read help for " + pathSearchString);
    }
  }
}
