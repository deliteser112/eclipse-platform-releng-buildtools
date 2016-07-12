// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

import static com.google.common.net.HttpHeaders.X_FRAME_OPTIONS;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.appengine.api.users.UserService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.io.Resources;
import com.google.common.net.MediaType;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.tofu.SoyTofu;
import google.registry.config.ConfigModule.Config;
import google.registry.flows.EppConsoleAction;
import google.registry.model.registrar.Registrar;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.security.XsrfTokenManager;
import google.registry.ui.server.SoyTemplateUtils;
import google.registry.ui.soy.registrar.ConsoleSoyInfo;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/** Action that serves Registrar Console single HTML page (SPA). */
@Action(path = ConsoleUiAction.PATH, requireLogin = true, xsrfProtection = false)
public final class ConsoleUiAction implements Runnable {

  public static final String PATH = "/registrar";

  private static final Supplier<SoyTofu> TOFU_SUPPLIER =
      SoyTemplateUtils.createTofuSupplier(
          google.registry.ui.soy.ConsoleSoyInfo.getInstance(),
          google.registry.ui.soy.registrar.ConsoleSoyInfo.getInstance());

  @VisibleForTesting  // webdriver and screenshot tests need this
  public static final Supplier<SoyCssRenamingMap> CSS_RENAMING_MAP_SUPPLIER =
      SoyTemplateUtils.createCssRenamingMapSupplier(
          Resources.getResource("google/registry/ui/css/registrar_bin.css.js"),
          Resources.getResource("google/registry/ui/css/registrar_dbg.css.js"));

  @Inject HttpServletRequest req;
  @Inject Response response;
  @Inject SessionUtils sessionUtils;
  @Inject UserService userService;
  @Inject @Config("registrarConsoleEnabled") boolean enabled;
  @Inject ConsoleUiAction() {}

  @Override
  public void run() {
    response.setContentType(MediaType.HTML_UTF_8);
    response.setHeader(X_FRAME_OPTIONS, "SAMEORIGIN");  // Disallow iframing.
    response.setHeader("X-Ui-Compatible", "IE=edge");  // Ask IE not to be silly.
    if (!enabled) {
      response.setStatus(SC_SERVICE_UNAVAILABLE);
      response.setPayload(
          TOFU_SUPPLIER.get()
              .newRenderer(ConsoleSoyInfo.DISABLED)
              .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
              .render());
      return;
    }
    if (!sessionUtils.checkRegistrarConsoleLogin(req)) {
      SoyMapData data = new SoyMapData();
      data.put("username", userService.getCurrentUser().getNickname());
      data.put("logoutUrl", userService.createLogoutURL(PATH));
      response.setStatus(SC_FORBIDDEN);
      response.setPayload(
          TOFU_SUPPLIER.get()
              .newRenderer(ConsoleSoyInfo.WHOAREYOU)
              .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
              .setData(data)
              .render());
      return;
    }
    Registrar registrar = Registrar.loadByClientId(sessionUtils.getRegistrarClientId(req));
    SoyMapData data = new SoyMapData();
    data.put("xsrfToken", XsrfTokenManager.generateToken(EppConsoleAction.XSRF_SCOPE));
    data.put("clientId", registrar.getClientIdentifier());
    data.put("username", userService.getCurrentUser().getNickname());
    data.put("isAdmin", userService.isUserAdmin());
    data.put("logoutUrl", userService.createLogoutURL(PATH));
    data.put("showPaymentLink", registrar.getBillingMethod() == Registrar.BillingMethod.BRAINTREE);
    response.setPayload(
        TOFU_SUPPLIER.get()
            .newRenderer(ConsoleSoyInfo.MAIN)
            .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
            .setData(data)
            .render());
  }
}
