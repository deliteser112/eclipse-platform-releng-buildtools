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

package google.registry.security;

import static google.registry.security.XsrfTokenManager.X_CSRF_TOKEN;
import static google.registry.security.XsrfTokenManager.generateToken;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import google.registry.testing.AppEngineRule;
import google.registry.testing.UserInfo;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link XsrfProtectedServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class XsrfProtectedServletTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withUserService(UserInfo.create("test@example.com", "test@example.com"))
      .build();

  @Mock
  HttpServletRequest req;

  @Mock
  HttpServletResponse rsp;

  XsrfProtectedServlet servlet = new XsrfProtectedServlet("foo", false) {
      @Override
      protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
        rsp.setStatus(SC_OK);
      }};

  String validXsrfToken;

  @Before
  public void init() {
    this.validXsrfToken = generateToken("foo");
  }

  private void setup(String xsrf, String method) throws Exception {
    when(req.getHeader(X_CSRF_TOKEN)).thenReturn(xsrf);
    when(req.getMethod()).thenReturn(method);
    when(req.getServletPath()).thenReturn("");
  }

  @Test
  public void testSuccess() throws Exception {
    setup(validXsrfToken, "post");
    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
  }

  private void doInvalidRequestTest(String xsrf) throws Exception {
    setup(xsrf, "post");
    servlet.service(req, rsp);
    verify(rsp).sendError(eq(SC_FORBIDDEN), anyString());
  }

  @Test
  public void testFailure_badXsrfToken() throws Exception {
    doInvalidRequestTest("foo");
  }

  @Test
  public void testFailure_missingXsrfToken() throws Exception {
    doInvalidRequestTest(null);
  }

  @Test
  public void testFailure_notAdmin() throws Exception {
    setup(validXsrfToken, "post");
    new XsrfProtectedServlet("foo", true) {
      @Override
      protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
        rsp.setStatus(SC_OK);
      }}.service(req, rsp);
    verify(rsp).sendError(eq(SC_FORBIDDEN), anyString());
  }
}
