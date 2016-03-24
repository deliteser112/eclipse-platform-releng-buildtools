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

package com.google.domain.registry.util;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.domain.registry.util.PreconditionsUtils.checkArgumentNotNull;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Utility methods for working with {@link HttpServlet}-related classes. */
public final class HttpServletUtils {

  /**
   * Returns the value of the given request's first {@code name} parameter, or throws
   * {@code IllegalArgumentException} if the parameter is not present.
   */
  public static String getRequiredParameterValue(HttpServletRequest req, String name) {
    return checkArgumentNotNull(req.getParameter(name), "Missing required parameter: %s", name);
  }

  /**
   * Sets the content type on an HttpServletResponse to UTF-8, returns 200 OK status, and sends
   * "OK".
   */
  public static void sendOk(HttpServletResponse rsp) throws IOException {
    rsp.setContentType(PLAIN_TEXT_UTF_8.toString());
    rsp.getWriter().write("OK\n");
  }
}
