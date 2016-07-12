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

package google.registry.server;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet that wraps a servlet and delegates request execution to a queue.
 *
 * @see TestServer
 */
public final class ServletWrapperDelegatorServlet extends HttpServlet {

  private final Queue<FutureTask<Void>> requestQueue;
  private final Supplier<HttpServlet> servlet;

  ServletWrapperDelegatorServlet(
      Class<? extends HttpServlet> servletClass,
      Queue<FutureTask<Void>> requestQueue) {
    this.servlet = lazilyInstantiate(checkNotNull(servletClass, "servletClass"));
    this.requestQueue = checkNotNull(requestQueue, "requestQueue");
  }

  @Override
  public void service(final HttpServletRequest req, final HttpServletResponse rsp)
      throws ServletException, IOException {
    FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Nullable
      @Override
      public Void call() throws ServletException, IOException {
        servlet.get().service(req, rsp);
        return null;
      }});
    requestQueue.add(task);
    try {
      Uninterruptibles.getUninterruptibly(task);
    } catch (ExecutionException e) {
      Throwables.propagateIfInstanceOf(e.getCause(), ServletException.class);
      Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
      throw Throwables.propagate(e.getCause());
    }
  }

  private static <T> Supplier<T> lazilyInstantiate(final Class<? extends T> clazz) {
    return memoize(new Supplier<T>() {
      @Override
      public T get() {
        try {
          return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }});
  }
}
