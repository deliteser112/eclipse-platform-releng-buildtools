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

package com.google.domain.registry.request;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Action that delegates to a servlet.
 *
 * <p>This is a transitory solution for using legacy servlets inside the new injection action
 * framework.
 *
 * @param <T> servlet type, which must have an {@link Inject @Inject} constructor
 */
public abstract class ServletDelegate<T extends HttpServlet> implements Runnable {

  @Inject T servlet;
  @Inject HttpServletRequest req;
  @Inject HttpServletResponse rsp;

  protected ServletDelegate() {}

  @Override
  public final void run() {
    try {
      servlet.init();
      servlet.service(req, rsp);
      servlet.destroy();
    } catch (ServletException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
