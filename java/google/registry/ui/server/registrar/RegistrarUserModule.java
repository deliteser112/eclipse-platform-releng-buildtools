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

package google.registry.ui.server.registrar;

import dagger.Module;
import dagger.Provides;
import google.registry.model.registrar.Registrar;
import google.registry.request.HttpException.ForbiddenException;
import javax.servlet.http.HttpServletRequest;

/** Registrar Console module providing reference to logged-in {@link Registrar}. */
@Module
public final class RegistrarUserModule {

  @Provides
  static Registrar provideRegistrarUser(SessionUtils sessionUtils, HttpServletRequest req) {
    if (!sessionUtils.checkRegistrarConsoleLogin(req)) {
      throw new ForbiddenException("Not authorized to access Registrar Console");
    }
    return Registrar.loadByClientId(sessionUtils.getRegistrarClientId(req));
  }
}
