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

package google.registry.rdap;

import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
import google.registry.request.Action;
import google.registry.util.Clock;
import javax.inject.Inject;

/**
 * RDAP (new WHOIS) action for help requests.
 */
@Action(path = RdapHelpAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapHelpAction extends RdapActionBase {

  public static final String PATH = "/rdap/help";

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
    rdapJsonFormatter.addTopLevelEntries(
        builder,
        BoilerplateType.OTHER,
        ImmutableList.of(rdapJsonFormatter.getJsonHelpNotice(pathSearchString, rdapLinkBase)),
        ImmutableList.<ImmutableMap<String, Object>>of(),
        rdapLinkBase);
    return builder.build();
  }
}
