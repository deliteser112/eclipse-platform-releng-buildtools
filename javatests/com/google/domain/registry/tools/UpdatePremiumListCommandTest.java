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

package com.google.domain.registry.tools;

import static com.google.domain.registry.request.JsonResponse.JSON_SAFETY_PREFIX;
import static com.google.domain.registry.tools.CreateOrUpdatePremiumListCommandTestCase.generateInputData;
import static com.google.domain.registry.tools.CreateOrUpdatePremiumListCommandTestCase.verifySentParams;
import static com.google.domain.registry.util.ResourceUtils.readResourceUtf8;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import com.google.domain.registry.tools.ServerSideCommand.Connection;
import com.google.domain.registry.tools.server.UpdatePremiumListAction;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Unit tests for {@link UpdatePremiumListCommand}. */
public class UpdatePremiumListCommandTest<C extends UpdatePremiumListCommand>
    extends CommandTestCase<C> {

  @Mock
  Connection connection;

  String premiumTermsPath;
  String premiumTermsCsv;
  String servletPath;

  @Before
  public void init() throws Exception {
    command.setConnection(connection);
    servletPath = "/_dr/admin/updatePremiumList";
    premiumTermsPath = writeToTmpFile(readResourceUtf8(
        UpdatePremiumListCommandTest.class,
        "testdata/example_premium_terms.csv"));
    when(connection.send(
        eq(UpdatePremiumListAction.PATH),
        anyMapOf(String.class, String.class),
        eq(MediaType.PLAIN_TEXT_UTF_8),
        any(byte[].class)))
        .thenReturn(JSON_SAFETY_PREFIX + "{\"status\":\"success\",\"lines\":[]}");
  }

  @Test
  public void testRun() throws Exception {
    ImmutableMap<String, String> params =
        ImmutableMap.of("name", "foo", "inputData", generateInputData(premiumTermsPath));
    runCommandForced("-i=" + premiumTermsPath, "-n=foo");
    verifySentParams(connection, servletPath, params);
  }
}
