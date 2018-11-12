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

package google.registry.util;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;

/** A wrapper for {@link ModulesService} that provides a saner API. */
public class AppEngineServiceUtilsImpl implements AppEngineServiceUtils {

  private final ModulesService modulesService;

  @Inject
  public AppEngineServiceUtilsImpl(ModulesService modulesService) {
    this.modulesService = modulesService;
  }

  @Override
  public String getServiceHostname(String service) {
    // This will be in the format "version.service.projectid.appspot.com"
    String hostnameWithVersion = modulesService.getVersionHostname(service, null);
    // Strip off the version and return just "service.projectid.appspot.com"
    return hostnameWithVersion.replaceFirst("^[^.]+\\.", "");
  }

  @Override
  public String getCurrentVersionHostname(String service) {
    return modulesService.getVersionHostname(service, null);
  }

  @Override
  public String getVersionHostname(String service, String version) {
    checkArgumentNotNull(version, "Must specify the version");
    return modulesService.getVersionHostname(service, version);
  }

  @Override
  public void setNumInstances(String service, String version, long numInstances) {
    checkArgumentNotNull(service, "Must specify the service");
    checkArgumentNotNull(version, "Must specify the version");
    checkArgument(numInstances > 0, "Number of instances must be greater than 0");
    modulesService.setNumInstances(service, version, numInstances);
  }

  /** Dagger module for AppEngineServiceUtils. */
  @Module
  public abstract static class AppEngineServiceUtilsModule {

    private static final ModulesService modulesService = ModulesServiceFactory.getModulesService();

    @Provides
    static ModulesService provideModulesService() {
      return modulesService;
    }

    @Binds
    abstract AppEngineServiceUtils provideAppEngineServiceUtils(
        AppEngineServiceUtilsImpl appEngineServiceUtilsImpl);
  }
}
