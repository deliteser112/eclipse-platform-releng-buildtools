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

package google.registry.security;

import static com.google.appengine.api.users.UserServiceFactory.getUserService;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;
import static google.registry.security.XsrfTokenManager.X_CSRF_TOKEN;
import static google.registry.security.XsrfTokenManager.validateToken;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

import com.google.appengine.api.users.UserService;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.Duration;

/**
 * Servlet with Cross-Site Request Forgery (XSRF) protection.
 *
 * <p>This servlet enforces XSRF protection on all requests by checking the value provided in the
 * "X-CSRF-Token" header. It can also optionally enforce that only admin users can call it.
 *
 * <p>All servlets that handle client requests should use XSRF protection.
 */
public abstract class XsrfProtectedServlet extends HttpServlet {

  private static final Duration XSRF_VALIDITY = Duration.standardDays(1);

  /** Used to validate XSRF tokens. */
  private String xsrfScope;

  /** Whether to do a security check for admin status. */
  private boolean requireAdmin;

  /** Gets the XSRF scope for this servlet. */
  public String getScope() {
    return xsrfScope;
  }

  protected XsrfProtectedServlet(String xsrfScope, boolean requireAdmin) {
    this.xsrfScope = checkNotNull(xsrfScope);
    this.requireAdmin = requireAdmin;
  }

  @Override
  public final void service(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    if (!validateToken(nullToEmpty(req.getHeader(X_CSRF_TOKEN)), xsrfScope, XSRF_VALIDITY)) {
      rsp.sendError(SC_FORBIDDEN, "Invalid " + X_CSRF_TOKEN);
      return;
    }
    if (!validateAdmin()) {
      rsp.sendError(SC_FORBIDDEN, "Administrator access only");
      return;
    }
    doPost(req, rsp);
  }

  /**
   * If this is an admin-only servlet, require admin permissions or being in development mode. Such
   * servlets should primarily be defended by being marked internal-only in web.xml, but it's worth
   * adding a defense-in-depth.
   */
  private boolean validateAdmin() {
    UserService userService = getUserService();
    return requireAdmin ? (userService.isUserLoggedIn() && userService.isUserAdmin()) : true;
  }
}
