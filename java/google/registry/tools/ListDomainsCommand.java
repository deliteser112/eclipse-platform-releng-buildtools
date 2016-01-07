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

package google.registry.tools;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableMap;
import google.registry.tools.server.ListDomainsAction;

/** Command to list all second-level domains associated with a TLD. */
@Parameters(separators = " =", commandDescription = "List domains associated with a TLD.")
final class ListDomainsCommand extends ListObjectsCommand {

  @Parameter(
      names = {"-t", "--tld"},
      description = "Top level domain whose second-level domains shall be listed.",
      required = true)
  private String tld;

  @Override
  String getCommandPath() {
    return ListDomainsAction.PATH;
  }

  /** Returns a map of parameters to be sent to the server
   * (in addition to the usual ones). */
  @Override
  ImmutableMap<String, Object> getParameterMap() {
    return ImmutableMap.<String, Object>of("tld", tld);
  }
}
