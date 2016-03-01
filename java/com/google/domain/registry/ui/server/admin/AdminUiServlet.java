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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.io.Resources;
import com.google.domain.registry.ui.server.AbstractUiServlet;
import com.google.domain.registry.ui.server.SoyTemplateUtils;
import com.google.domain.registry.ui.soy.admin.ConsoleSoyInfo;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.tofu.SoyTofu;

/** UI for Registry operations. */
public class AdminUiServlet extends AbstractUiServlet {

  @VisibleForTesting
  static final Supplier<SoyTofu> TOFU_SUPPLIER =
      SoyTemplateUtils.createTofuSupplier(
          com.google.domain.registry.ui.soy.ConsoleSoyInfo.getInstance(),
          com.google.domain.registry.ui.soy.admin.ConsoleSoyInfo.getInstance());

  public static final Supplier<SoyCssRenamingMap> CSS_RENAMING_MAP_SUPPLIER =
      SoyTemplateUtils.createCssRenamingMapSupplier(
          Resources.getResource("com/google/domain/registry/ui/css/admin_bin.css.js"),
          Resources.getResource("com/google/domain/registry/ui/css/admin_dbg.css.js"));

  @Override
  protected String get() {
    return TOFU_SUPPLIER.get()
        .newRenderer(ConsoleSoyInfo.MAIN)
        .setCssRenamingMap(CSS_RENAMING_MAP_SUPPLIER.get())
        .setData(getTemplateArgs(AdminResourceServlet.XSRF_SCOPE))
        .render();
  }
}
