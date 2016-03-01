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

package com.google.domain.registry.ui.server.admin;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.config.RegistryConfig;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.request.HttpException.NotFoundException;
import com.google.domain.registry.security.JsonResponseHelper;
import com.google.domain.registry.security.JsonTransportServlet;
import com.google.domain.registry.ui.forms.FormFieldException;

import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

/** A servlet for callbacks that manipulate resources. */
public abstract class AdminResourceServlet extends JsonTransportServlet {

  private static final RegistryConfig CONFIG = RegistryEnvironment.get().config();
  public static final String XSRF_SCOPE = "admin";

  public AdminResourceServlet() {
    super(XSRF_SCOPE, true);
  }

  @Override
  public Map<String, Object> doJsonPost(HttpServletRequest req, Map<String, ?> params) {
    String op = Optional.fromNullable((String) params.get("op")).or("read");
    @SuppressWarnings("unchecked")
    Map<String, ?> args = (Map<String, Object>) Optional.<Object>fromNullable(params.get("args"))
        .or(ImmutableMap.of());
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
          throw new UnsupportedOperationException("Unknown operation: " + op);
      }
    } catch (FormFieldException e) {
      return JsonResponseHelper.createFormFieldError(e.getMessage(), e.getFieldName());
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

  @SuppressWarnings("unchecked")
  protected static <T> ImmutableList<T> getParamList(Map<String, ?> map, String identifier) {
    return ImmutableList.copyOf(
        Optional.fromNullable((Iterable<T>) map.get(identifier)).or(ImmutableList.<T>of()));
  }

  /** Like checkNotNull, but throws NotFoundException if given arg is null. */
  protected static <T> T checkExists(@Nullable T obj, String msg) {
    if (obj == null) {
      throw new NotFoundException(msg);
    }
    return obj;
  }

  protected static String getValAsString(Map<String, ?> map, String identifier) {
    return (String) map.get(identifier);
  }

  /** Returns the last path element or null if no path separator exists. */
  @VisibleForTesting
  String parsePath(HttpServletRequest req) {
    String uri = req.getRequestURI();
    String prefix = CONFIG.getAdminServletPathPrefix() + "/";
    checkArgument(
        uri.startsWith(prefix),
        "Request URI must start with: %s",
        prefix);
    return uri.substring(prefix.length());
  }

  /** @return the last path element or null if no path separator exists. */
  @Nullable
  protected String parseId(HttpServletRequest req) {
    String[] pathParts = parsePath(req).split("/");
    return pathParts.length < 2 ? null : pathParts[pathParts.length - 1];
  }

  /** Like parseId but path must contain at least one path separator. */
  protected String checkParseId(HttpServletRequest req) {
    return checkNotNull(parseId(req), "Path must be of the form (/<collection>)+/<id>");
  }
}
