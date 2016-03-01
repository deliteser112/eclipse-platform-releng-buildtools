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

package com.google.domain.registry.ui.server;

import static com.google.domain.registry.security.XsrfTokenManager.generateToken;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.net.MediaType;
import com.google.template.soy.data.SoyMapData;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Abstract servlet for serving HTML pages. */
public abstract class AbstractUiServlet extends HttpServlet {

  protected String userId;
  protected String userName;
  protected String userActionName;
  protected String userActionHref;
  protected boolean userIsAdmin;

  @Override
  public void service(HttpServletRequest req, HttpServletResponse rsp)
      throws ServletException, IOException {
    UserService userService = UserServiceFactory.getUserService();
    if (userService.isUserLoggedIn()) {
      User u = userService.getCurrentUser();
      userId = u.getUserId();
      userName = u.getNickname();
      userActionName = "Sign out";
      userActionHref = userService.createLogoutURL(req.getRequestURI());
      userIsAdmin = userService.isUserAdmin();
    } else {
      userId = null;
      userName = null;
      userActionName = "Sign in";
      userActionHref = userService.createLoginURL(req.getRequestURI());
      userIsAdmin = false;
    }
    super.service(req, rsp);
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws ServletException, IOException {
    rsp.addHeader("X-Frame-Options", "SAMEORIGIN");  // Disallow iframing.
    rsp.setHeader("X-Ui-Compatible", "IE=edge");  // Ask IE not to be silly.
    rsp.setContentType(MediaType.HTML_UTF_8.toString());
    UserService userService = UserServiceFactory.getUserService();
    if (!userService.isUserLoggedIn()) {
      rsp.sendRedirect(userService.createLoginURL(req.getRequestURI()));
      return;
    }
    rsp.getWriter().write(get(req));
  }

  /**
   * Subclasses may override this method to access request params, or
   * get() to simply return content.
   */
  protected String get(@SuppressWarnings("unused") HttpServletRequest req) {
    return get();
  }

  /** Override this to just return content. */
  protected String get() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns a map with {@code (user: (id,name,actionName,actionHref), gaeUserId:, xsrfToken:)}
   */
  protected SoyMapData getTemplateArgs(String xsrfToken) {
    SoyMapData user = new SoyMapData();
    user.put("id", userId);
    user.put("name", userName);
    user.put("actionName", userActionName);
    user.put("actionHref", userActionHref);
    user.put("isAdmin", userIsAdmin);
    SoyMapData result = new SoyMapData();
    result.put("user", user);
    result.put("gaeUserId", userId);
    result.put("xsrfToken", generateToken(xsrfToken));
    return result;
  }
}
