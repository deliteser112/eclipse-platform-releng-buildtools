// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.flogger.FluentLogger;
import google.registry.util.AppEngineServiceUtils;
import javax.inject.Inject;

/** A command to set the number of instances for an App Engine service. */
@Parameters(
    separators = " =",
    commandDescription =
        "Set the number of instances for a given service and version. "
            + "Note that this command only works for manual scaling service.")
final class SetNumInstancesCommand implements CommandWithRemoteApi {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Parameter(
      names = "--service",
      description = "Name of the App Engine service, e.g., default.",
      required = true)
  private String service;

  @Parameter(
      names = "--version",
      description = "Name of the service's version, e.g., canary.",
      required = true)
  private String version;

  @Parameter(
      names = "--numInstances",
      description = "The new number of instances for the version.",
      required = true)
  private Long numInstances;

  @Inject AppEngineServiceUtils appEngineServiceUtils;

  @Override
  public void run() throws Exception {
    checkArgument(!service.isEmpty(), "Service must be specified");
    checkArgument(!version.isEmpty(), "Version must be specified");
    checkArgument(numInstances > 0, "Number of instances must be greater than zero");

    appEngineServiceUtils.setNumInstances(service, version, numInstances);
    logger.atInfo().log(
        "Successfully set version %s of service %s to %d instances.",
        version, service, numInstances);
  }
}
