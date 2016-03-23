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

package com.google.domain.registry.server;

import static com.google.domain.registry.server.Route.route;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Lightweight HTTP server for testing the Domain Registry Admin and Registrar consoles. */
public final class RegistryTestServer {

  public static final ImmutableMap<String, Path> RUNFILES =
      new ImmutableMap.Builder<String, Path>()
          .put("/index.html", Paths.get("java/com/google/domain/registry/ui/html/index.html"))
          .put("/error.html", Paths.get("java/com/google/domain/registry/ui/html/error.html"))
          .put("/assets/js/*", Paths.get("java/com/google/domain/registry/ui"))
          .put("/assets/css/*", Paths.get("java/com/google/domain/registry/ui/css"))
          .put("/assets/sources/deps-runfiles.js",
              Paths.get("java/com/google/domain/registry/ui/deps-runfiles.js"))
          .put("/assets/sources/*", Paths.get(""))
          .put("/assets/*", Paths.get("java/com/google/domain/registry/ui/assets"))
          .build();

  private static final ImmutableList<Route> ROUTES = ImmutableList.of(
      // Frontend Services
      route("/whois/*", com.google.domain.registry.module.frontend.FrontendServlet.class),
      route("/rdap/*", com.google.domain.registry.module.frontend.FrontendServlet.class),
      route("/registrar-xhr", com.google.domain.registry.flows.EppConsoleServlet.class),
      route("/check", com.google.domain.registry.ui.server.api.CheckApiServlet.class),

      // Proxy Services
      route("/_dr/epp", com.google.domain.registry.flows.EppTlsServlet.class),
      route("/_dr/whois", com.google.domain.registry.module.frontend.FrontendServlet.class),

      // Registry Data Escrow (RDE)
      route("/_dr/cron/rdeCreate", com.google.domain.registry.module.backend.BackendServlet.class),
      route("/_dr/task/rdeStaging", com.google.domain.registry.module.backend.BackendServlet.class),
      route("/_dr/task/rdeUpload", com.google.domain.registry.module.backend.BackendServlet.class),
      route("/_dr/task/rdeReport", com.google.domain.registry.module.backend.BackendServlet.class),
      route("/_dr/task/brdaCopy", com.google.domain.registry.module.backend.BackendServlet.class),

      // Trademark Clearinghouse (TMCH)
      route("/_dr/cron/tmchDnl", com.google.domain.registry.module.backend.BackendServlet.class),
      route("/_dr/task/tmchSmdrl", com.google.domain.registry.module.backend.BackendServlet.class),
      route("/_dr/task/tmchCrl", com.google.domain.registry.module.backend.BackendServlet.class),

      // Notification of Registered Domain Names (NORDN)
      route("/_dr/task/nordnUpload",
          com.google.domain.registry.module.backend.BackendServlet.class),
      route("/_dr/task/nordnVerify",
          com.google.domain.registry.module.backend.BackendServlet.class),

      // Registrar Console
      route("/registrar", com.google.domain.registry.module.frontend.FrontendServlet.class),
      route("/registrar-settings",
          com.google.domain.registry.ui.server.registrar.RegistrarServlet.class),
      route("/registrar-payment",
          com.google.domain.registry.module.frontend.FrontendServlet.class),
      route("/registrar-payment-setup",
          com.google.domain.registry.module.frontend.FrontendServlet.class));

  private final TestServer server;

  /** @see TestServer#TestServer(HostAndPort, java.util.Map, Iterable) */
  public RegistryTestServer(HostAndPort address) {
    server = new TestServer(address, RUNFILES, ROUTES);
  }

  /** @see TestServer#start() */
  public void start() {
    server.start();
  }

  /** @see TestServer#process() */
  public void process() throws InterruptedException {
    server.process();
  }

  /** @see TestServer#stop() */
  public void stop() {
    server.stop();
  }

  /** @see TestServer#getUrl(java.lang.String) */
  public URL getUrl(String path) {
    return server.getUrl(path);
  }
}
