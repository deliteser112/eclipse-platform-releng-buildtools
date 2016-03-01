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

import static com.google.appengine.api.users.UserServiceFactory.getUserService;
import static com.google.domain.registry.flows.EppConsoleServlet.XSRF_SCOPE;
import static com.google.domain.registry.security.JsonResponseHelper.Status.ERROR;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.request.HttpException.NotFoundException;
import com.google.domain.registry.security.JsonResponseHelper;
import com.google.domain.registry.security.JsonTransportServlet;
import com.google.domain.registry.ui.forms.FormException;
import com.google.domain.registry.ui.forms.FormFieldException;
import com.google.domain.registry.util.NonFinalForTesting;

import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

/** A servlet for callbacks that manipulate resources. */
public abstract class ResourceServlet extends JsonTransportServlet {

  private static final String OP_PARAM = "op";
  private static final String ARGS_PARAM = "args";

  @NonFinalForTesting
  protected static SessionUtils sessionUtils = new SessionUtils(getUserService());

  public ResourceServlet() {
    super(XSRF_SCOPE, false);
  }

  @Override
  public Map<String, Object> doJsonPost(HttpServletRequest req, Map<String, ?> params) {
    if (!sessionUtils.isLoggedIn()) {
      return JsonResponseHelper.create(ERROR, "Not logged in");
    }
    if (!sessionUtils.checkRegistrarConsoleLogin(req)) {
      return JsonResponseHelper.create(ERROR, "Not authorized to access Registrar Console");
    }
    String op = Optional.fromNullable((String) params.get(OP_PARAM)).or("read");
    @SuppressWarnings("unchecked")
    Map<String, ?> args = (Map<String, Object>)
        Optional.<Object>fromNullable(params.get(ARGS_PARAM)).or(ImmutableMap.of());
    try {
      switch (op) {
        case "create":
          return create(req, args);
        case "update":
          return update(req, args);
        case "delete":
          return delete(req, args);
        case "read":
          return read(req, args);
        default:
          return JsonResponseHelper.create(ERROR, "Unknown operation: " + op);
      }
    } catch (FormFieldException e) {
      return JsonResponseHelper.createFormFieldError(e.getMessage(), e.getFieldName());
    } catch (FormException ee) {
      return JsonResponseHelper.create(ERROR, ee.getMessage());
    }
  }

  @SuppressWarnings("unused")
  Map<String, Object> create(HttpServletRequest req, Map<String, ?> args) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unused")
  Map<String, Object> read(HttpServletRequest req, Map<String, ?> args) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unused")
  Map<String, Object> update(HttpServletRequest req, Map<String, ?> args) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unused")
  Map<String, Object> delete(HttpServletRequest req, Map<String, ?> args) {
    throw new UnsupportedOperationException();
  }

  /** Like checkNotNull, but throws NotFoundException if given arg is null. */
  protected static <T> T checkExists(@Nullable T obj, String msg) {
    if (obj == null) {
      throw new NotFoundException(msg);
    }
    return obj;
  }
}
