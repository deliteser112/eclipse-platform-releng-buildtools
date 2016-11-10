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
import com.google.common.base.Optional;
import google.registry.util.FormattingLogger;
import google.registry.util.TypeUtils.TypeInstantiator;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.Duration;

/**
 * Dagger-based request processor.
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
 * @param <C> request component type
 * @param <B> builder for the request component
 */
public class RequestHandler<C, B extends RequestComponentBuilder<C, B>> {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private static final Duration XSRF_VALIDITY = Duration.standardDays(1);

  private final Router router;
  private final Provider<B> requestComponentBuilderProvider;
  private final UserService userService;

  /**
   * Constructor for subclasses to create a new request handler for a specific request component.
   *
   * <p>This operation will generate a routing map for the component's {@code @Action}-returning
   * methods using reflection, which is moderately expensive, so a given servlet should construct a
   * single {@code RequestHandler} and re-use it across requests.
   *
   * @param requestComponentBuilderProvider a Dagger {@code Provider} of builder instances that can
   *     be used to construct new instances of the request component (with the required
   *     request-derived modules provided by this class)
   * @param userService an instance of the App Engine UserService API
   */
  protected RequestHandler(Provider<B> requestComponentBuilderProvider, UserService userService) {
    this(null, requestComponentBuilderProvider, userService);
  }

  /** Creates a new RequestHandler with an explicit component class for test purposes. */
  public static <C, B extends RequestComponentBuilder<C, B>> RequestHandler<C, B> createForTest(
      Class<C> component, Provider<B> requestComponentBuilderProvider, UserService userService) {
    return new RequestHandler<>(
        checkNotNull(component), requestComponentBuilderProvider, userService);
  }

  private RequestHandler(
      @Nullable Class<C> component,
      Provider<B> requestComponentBuilderProvider,
      UserService userService) {
    // If the component class isn't explicitly provided, infer it from the class's own typing.
    // This is safe only for use by subclasses of RequestHandler where the generic parameter is
    // preserved at runtime, so only expose that option via the protected constructor.
    this.router = Router.create(
        component != null ? component : new TypeInstantiator<C>(getClass()){}.getExactType());
    this.requestComponentBuilderProvider = checkNotNull(requestComponentBuilderProvider);
    this.userService = checkNotNull(userService);
  }

  /** Runs the appropriate action for a servlet request. */
  public void handleRequest(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    checkNotNull(req);
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
    // Build a new request component using any modules we've constructed by this point.
    C component = requestComponentBuilderProvider.get()
        .requestModule(new RequestModule(req, rsp))
        .build();
    // Apply the selected Route to the component to produce an Action instance, and run it.
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
