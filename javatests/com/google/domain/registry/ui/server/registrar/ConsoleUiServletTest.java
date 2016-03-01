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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.domain.registry.config.TestRegistryConfig;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.RegistryConfigRule;
import com.google.domain.registry.testing.UserInfo;
import com.google.domain.registry.ui.soy.registrar.ConsoleSoyInfo;
import com.google.template.soy.data.SoyMapData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Unit tests for {@link ConsoleUiServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class ConsoleUiServletTest {

  @Rule
  public AppEngineRule appEngineRule = AppEngineRule.builder()
      .withUserService(UserInfo.create("foo@bar.com", "12345"))
      .build();

  @Rule
  public final RegistryConfigRule configRule = new RegistryConfigRule();

  @Mock
  HttpServletRequest req;

  @Mock
  HttpServletResponse rsp;

  final ConsoleUiServlet servlet = new ConsoleUiServlet();

  final StringWriter stringWriter = new StringWriter();

  @Before
  public void setUp() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(rsp.getWriter()).thenReturn(new PrintWriter(stringWriter));
    when(req.getRequestURI()).thenReturn("/registrar");
  }

  @Test
  public void testTofuCompilation() throws Exception {
    ConsoleUiServlet.TOFU_SUPPLIER.get();
  }

  @Test
  public void testTofuRender() throws Exception {
    SoyMapData data = new SoyMapData();
    SoyMapData user = new SoyMapData();
    user.put("name", "lol");
    user.put("actionName", "lol");
    user.put("actionHref", "lol");
    data.put("user", user);
    ConsoleUiServlet.TOFU_SUPPLIER.get()
        .newRenderer(ConsoleSoyInfo.WHOAREYOU)
        .setCssRenamingMap(ConsoleUiServlet.CSS_RENAMING_MAP_SUPPLIER.get())
        .setData(data)
        .render();
  }

  @Test
  public void testGet_consoleDisabled() throws Exception {
    configRule.override(new TestRegistryConfig() {
      @Override
      public boolean isRegistrarConsoleEnabled() {
        return false;
      }});
    servlet.service(req, rsp);
    assertThat(stringWriter.toString()).contains("Console is disabled");
  }
}
