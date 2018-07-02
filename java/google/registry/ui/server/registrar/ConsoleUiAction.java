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

import static com.google.common.net.HttpHeaders.LOCATION;
import static com.google.common.net.HttpHeaders.X_FRAME_OPTIONS;
import static google.registry.util.PreconditionsUtils.checkArgumentPresent;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_TEMPORARILY;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.appengine.api.users.UserService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.io.Resources;
import com.google.common.net.MediaType;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.tofu.SoyTofu;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.registrar.Registrar;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.security.XsrfTokenManager;
import google.registry.ui.server.SoyTemplateUtils;
import google.registry.ui.soy.registrar.ConsoleSoyInfo;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/** Action that serves Registrar Console single HTML page (SPA). */
@Action(
  path = ConsoleUiAction.PATH,
  auth = Auth.AUTH_PUBLIC
)
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
  @Inject XsrfTokenManager xsrfTokenManager;
  @Inject AuthResult authResult;
  @Inject @Config("logoFilename") String logoFilename;
  @Inject @Config("productName") String productName;
  @Inject @Config("integrationEmail") String integrationEmail;
  @Inject @Config("supportEmail") String supportEmail;
  @Inject @Config("announcementsEmail") String announcementsEmail;
  @Inject @Config("supportPhoneNumber") String supportPhoneNumber;
  @Inject @Config("technicalDocsUrl") String technicalDocsUrl;
  @Inject @Config("registrarConsoleEnabled") boolean enabled;
  @Inject ConsoleUiAction() {}

  @Override
  public void run() {
    if (!authResult.userAuthInfo().isPresent()) {
      response.setStatus(SC_MOVED_TEMPORARILY);
      String location;
      try {
        location = userService.createLoginURL(req.getRequestURI());
      } catch (IllegalArgumentException e) {
        // UserServiceImpl.createLoginURL() throws IllegalArgumentException if underlying API call
        // returns an error code of NOT_ALLOWED. createLoginURL() assumes that the error is caused
        // by an invalid URL. But in fact, the error can also occur if UserService doesn't have any
        // user information, which happens when the request has been authenticated as internal. In
        // this case, we want to avoid dying before we can send the redirect, so just redirect to
        // the root path.
        location = "/";
      }
      response.setHeader(LOCATION, location);
      return;
    }
    UserAuthInfo userAuthInfo = authResult.userAuthInfo().get();
    response.setContentType(MediaType.HTML_UTF_8);
    response.setHeader(X_FRAME_OPTIONS, "SAMEORIGIN");  // Disallow iframing.
    response.setHeader("X-Ui-Compatible", "IE=edge");  // Ask IE not to be silly.
    SoyMapData data = new SoyMapData();
    data.put("logoFilename", logoFilename);
    data.put("productName", productName);
    data.put("integrationEmail", integrationEmail);
    data.put("supportEmail", supportEmail);
    data.put("announcementsEmail", announcementsEmail);
    data.put("supportPhoneNumber", supportPhoneNumber);
    data.put("technicalDocsUrl", technicalDocsUrl);
    if (!enabled) {
      response.setStatus(SC_SERVICE_UNAVAILABLE);
      response.setPayload(
          TOFU_SUPPLIER.get()
              .newRenderer(ConsoleSoyInfo.DISABLED)
              .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
              .setData(data)
              .render());
      return;
    }
    data.put("username", userAuthInfo.user().getNickname());
    data.put("logoutUrl", userService.createLogoutURL(PATH));
    if (!sessionUtils.checkRegistrarConsoleLogin(req, userAuthInfo)) {
      response.setStatus(SC_FORBIDDEN);
      response.setPayload(
          TOFU_SUPPLIER.get()
              .newRenderer(ConsoleSoyInfo.WHOAREYOU)
              .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
              .setData(data)
              .render());
      return;
    }
    String clientId = sessionUtils.getRegistrarClientId(req);
    Registrar registrar =
        checkArgumentPresent(
            Registrar.loadByClientIdCached(clientId), "Registrar %s does not exist", clientId);
    data.put("xsrfToken", xsrfTokenManager.generateToken(userAuthInfo.user().getEmail()));
    data.put("clientId", clientId);
    data.put("requireFeeExtension", registrar.getPremiumPriceAckRequired());

    String payload = TOFU_SUPPLIER.get()
            .newRenderer(ConsoleSoyInfo.MAIN)
            .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
            .setData(data)
            .render();
    response.setPayload(payload);
  }
}
