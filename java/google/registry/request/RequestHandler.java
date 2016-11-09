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

package google.registry.request;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.net.HttpHeaders.LOCATION;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static google.registry.security.XsrfTokenManager.X_CSRF_TOKEN;
import static google.registry.security.XsrfTokenManager.validateToken;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_TEMPORARILY;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.base.Optional;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.Duration;

/**
 * Dagger request processor for Nomulus.
 *
 * <p>This class creates an HTTP request processor from a Dagger component. It routes requests from
 * your servlet to an {@link Action @Action} annotated handler class.
 *
 * <h3>Component Definition</h3>
 *
 * <p>Action instances are supplied on a per-request basis by invoking the methods on {@code C}.
 * For example:
 * <pre>
 * {@literal @Component}
 * interface ServerComponent {
 *   HelloAction helloAction();
 * }</pre>
 *
 * <p>The rules for component methods are as follows:
 * <ol>
 * <li>Methods whose raw return type does not implement {@code Runnable} will be ignored
 * <li>Methods whose raw return type does not have an {@code @Action} annotation are ignored
 * </ol>
 *
 * <h3>Security Features</h3>
 *
 * <p>XSRF protection is built into this class. It can be enabled or disabled on individual actions
 * using {@link Action#xsrfProtection() xsrfProtection} setting.
 *
 * <p>This class also enforces the {@link Action#requireLogin() requireLogin} setting.
 *
 * @param <C> component type
 */
public final class RequestHandler<C> {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private static final Duration XSRF_VALIDITY = Duration.standardDays(1);

  @NonFinalForTesting
  private static UserService userService = UserServiceFactory.getUserService();

  /** Creates a new request processor based off your component methods. */
  public static <C> RequestHandler<C> create(Class<C> component) {
    return new RequestHandler<>(component, Router.create(component));
  }

  private final Router router;

  private RequestHandler(Class<C> component, Router router) {
    checkNotNull(component);
    this.router = router;
  }

  /**
   * Runs the appropriate action for a servlet request.
   *
   * @param component is an instance of the component type passed to {@link #create(Class)}
   */
  public void handleRequest(HttpServletRequest req, HttpServletResponse rsp, C component)
      throws IOException {
    checkNotNull(component);
    checkNotNull(rsp);
    Action.Method method;
    try {
      method = Action.Method.valueOf(req.getMethod());
    } catch (IllegalArgumentException e) {
      logger.infofmt("Unsupported method: %s", req.getMethod());
      rsp.sendError(SC_METHOD_NOT_ALLOWED);
      return;
    }
    String path = req.getRequestURI();
    Optional<Route> route = router.route(path);
    if (!route.isPresent()) {
      logger.infofmt("No action found for: %s", path);
      rsp.sendError(SC_NOT_FOUND);
      return;
    }
    if (!route.get().isMethodAllowed(method)) {
      logger.infofmt("Method %s not allowed for: %s", method, path);
      rsp.sendError(SC_METHOD_NOT_ALLOWED);
      return;
    }
    if (route.get().action().requireLogin() && !userService.isUserLoggedIn()) {
      logger.info("not logged in");
      rsp.setStatus(SC_MOVED_TEMPORARILY);
      rsp.setHeader(LOCATION, userService.createLoginURL(req.getRequestURI()));
      return;
    }
    if (route.get().shouldXsrfProtect(method)
        && !validateToken(
                nullToEmpty(req.getHeader(X_CSRF_TOKEN)),
                route.get().action().xsrfScope(),
                XSRF_VALIDITY)) {
      rsp.sendError(SC_FORBIDDEN, "Invalid " + X_CSRF_TOKEN);
      return;
    }
    try {
      route.get().instantiator().apply(component).run();
      if (route.get().action().automaticallyPrintOk()) {
        rsp.setContentType(PLAIN_TEXT_UTF_8.toString());
        rsp.getWriter().write("OK\n");
      }
    } catch (HttpException e) {
      e.send(rsp);
    }
  }
}
