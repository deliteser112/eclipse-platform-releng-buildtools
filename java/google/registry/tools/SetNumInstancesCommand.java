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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.api.services.appengine.v1.Appengine;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.tools.AppEngineConnection.Service;
import google.registry.util.AppEngineServiceUtils;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;

/** A command to set the number of instances for an App Engine service. */
@Parameters(
    separators = " =",
    commandDescription =
        "Set the number of instances for a given service and version. "
            + "Note that this command only works for manual scaling service.")
final class SetNumInstancesCommand implements CommandWithRemoteApi {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> ALL_VALID_SERVICES =
      Arrays.stream(Service.values()).map(Service::name).collect(toImmutableSet());

  private static final ImmutableSet<String> ALL_DEPLOYED_SERVICE_IDS =
      Arrays.stream(Service.values()).map(Service::getServiceId).collect(toImmutableSet());

  // TODO(b/119629679): Use List<Service> after upgrading jcommander to latest version.
  @Parameter(
      names = "--services",
      description =
          "Comma-delimited list of App Engine services to set. "
              + "Allowed values: [DEFAULT, TOOLS, BACKEND, PUBAPI]")
  private List<String> services = ImmutableList.of();

  @Parameter(
      names = "--versions",
      description =
          "Comma-delimited list of App Engine versions to set, e.g., canary. "
              + "Cannot be set if --non_live_versions is set.")
  private List<String> versions = ImmutableList.of();

  @Parameter(
      names = "--num_instances",
      description =
          "The new number of instances for the given versions "
              + "or for all non-live versions if --non_live_versions is set.",
      required = true)
  private Long numInstances;

  @Parameter(
      names = "--non_live_versions",
      description = "Whether to set number of instances for all non-live versions.",
      arity = 1)
  private Boolean nonLiveVersions = false;

  @Inject AppEngineServiceUtils appEngineServiceUtils;
  @Inject Appengine appengine;

  @Inject
  @Config("projectId")
  String projectId;

  @Override
  public void run() throws Exception {
    Set<String> invalidServiceIds =
        Sets.difference(ImmutableSet.copyOf(services), ALL_VALID_SERVICES);
    checkArgument(invalidServiceIds.isEmpty(), "Invalid service(s): %s", invalidServiceIds);

    Set<String> serviceIds =
        services.stream()
            .map(service -> Service.valueOf(service).getServiceId())
            .collect(toImmutableSet());

    if (nonLiveVersions) {
      checkArgument(versions.isEmpty(), "--versions cannot be set if --non_live_versions is set");

      serviceIds = serviceIds.isEmpty() ? ALL_DEPLOYED_SERVICE_IDS : serviceIds;
      Multimap<String, String> allLiveVersionsMap = getAllLiveVersionsMap(serviceIds);
      Multimap<String, String> manualScalingVersionsMap = getManualScalingVersionsMap(serviceIds);

      // Set number of instances for versions which are manual scaling and non-live
      manualScalingVersionsMap.forEach(
          (serviceId, versionId) -> {
            if (!allLiveVersionsMap.containsEntry(serviceId, versionId)) {
              setNumInstances(serviceId, versionId, numInstances);
            }
          });
    } else {
      checkArgument(!serviceIds.isEmpty(), "Service must be specified");
      checkArgument(!versions.isEmpty(), "Version must be specified");
      checkArgument(numInstances > 0, "Number of instances must be greater than zero");

      Multimap<String, String> manualScalingVersionsMap = getManualScalingVersionsMap(serviceIds);

      for (String serviceId : serviceIds) {
        for (String versionId : versions) {
          checkArgument(
              manualScalingVersionsMap.containsEntry(serviceId, versionId),
              "Version %s of service %s is not managed through manual scaling",
              versionId,
              serviceId);
          setNumInstances(serviceId, versionId, numInstances);
        }
      }
    }
  }

  private void setNumInstances(String service, String version, long numInstances) {
    appEngineServiceUtils.setNumInstances(service, version, numInstances);
    logger.atInfo().log(
        "Successfully set version %s of service %s to %d instances.",
        version, service, numInstances);
  }

  private Multimap<String, String> getAllLiveVersionsMap(Set<String> services) {
    try {
      return Stream.of(appengine.apps().services().list(projectId).execute().getServices())
          .flatMap(Collection::stream)
          .filter(service -> services.contains(service.getId()))
          .flatMap(
              service ->
                  // getAllocations returns only live versions or null
                  Stream.of(service.getSplit().getAllocations())
                      .flatMap(
                          allocations ->
                              allocations.keySet().stream()
                                  .map(versionId -> new SimpleEntry<>(service.getId(), versionId))))
          .collect(
              Multimaps.toMultimap(
                  SimpleEntry::getKey,
                  SimpleEntry::getValue,
                  MultimapBuilder.treeKeys().arrayListValues()::build));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Multimap<String, String> getManualScalingVersionsMap(Set<String> services) {
    return services.stream()
        .flatMap(
            serviceId -> {
              try {
                return Stream.of(
                        appengine
                            .apps()
                            .services()
                            .versions()
                            .list(projectId, serviceId)
                            .execute()
                            .getVersions())
                    .flatMap(Collection::stream)
                    .filter(version -> version.getManualScaling() != null)
                    .map(version -> new SimpleEntry<>(serviceId, version.getId()));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(
            Multimaps.toMultimap(
                SimpleEntry::getKey,
                SimpleEntry::getValue,
                MultimapBuilder.treeKeys().arrayListValues()::build));
  }
}
