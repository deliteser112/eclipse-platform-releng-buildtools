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

package com.google.domain.registry.ui.server.registrar;

import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.io.Resources;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.flows.EppConsoleServlet;
import com.google.domain.registry.ui.server.AbstractUiServlet;
import com.google.domain.registry.ui.server.SoyTemplateUtils;
import com.google.domain.registry.ui.soy.registrar.ConsoleSoyInfo;
import com.google.domain.registry.util.NonFinalForTesting;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.tofu.SoyTofu;

import javax.servlet.http.HttpServletRequest;

/** Main registrar console servlet that serves the client code. */
public final class ConsoleUiServlet extends AbstractUiServlet {

  @VisibleForTesting
  static final Supplier<SoyTofu> TOFU_SUPPLIER =
      SoyTemplateUtils.createTofuSupplier(
          com.google.domain.registry.ui.soy.ConsoleSoyInfo.getInstance(),
          com.google.domain.registry.ui.soy.registrar.ConsoleSoyInfo.getInstance());

  @VisibleForTesting
  public static final Supplier<SoyCssRenamingMap> CSS_RENAMING_MAP_SUPPLIER =
      SoyTemplateUtils.createCssRenamingMapSupplier(
          Resources.getResource("com/google/domain/registry/ui/css/registrar_bin.css.js"),
          Resources.getResource("com/google/domain/registry/ui/css/registrar_dbg.css.js"));

  @NonFinalForTesting
  private static SessionUtils sessionUtils = new SessionUtils(UserServiceFactory.getUserService());

  @Override
  protected String get(HttpServletRequest req) {
    if (!RegistryEnvironment.get().config().isRegistrarConsoleEnabled()) {
      return TOFU_SUPPLIER.get()
          .newRenderer(ConsoleSoyInfo.DISABLED)
          .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
          .render();
    }

    SoyMapData data = getTemplateArgs(EppConsoleServlet.XSRF_SCOPE);
    if (!sessionUtils.checkRegistrarConsoleLogin(req)) {
      data.getMapData("user").put("actionName", "Logout and switch to another account");
      return TOFU_SUPPLIER.get()
          .newRenderer(ConsoleSoyInfo.WHOAREYOU)
          .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
          .setData(data)
          .render();
    }
    data.put("clientId", req.getSession().getAttribute(SessionUtils.CLIENT_ID_ATTRIBUTE));
    return TOFU_SUPPLIER.get()
          .newRenderer(ConsoleSoyInfo.MAIN)
          .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
          .setData(data)
          .render();
  }
}
