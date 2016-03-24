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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import com.google.domain.registry.tools.ServerSideCommand.Connection;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for common testing setup for create and update commands for Premium Lists.
 */
public final class CreateOrUpdatePremiumListCommandTestCase {

  static String generateInputData(String premiumTermsPath) throws Exception {
    Path inputFile = Paths.get(premiumTermsPath);
    String data = new String(java.nio.file.Files.readAllBytes(inputFile));
    return data;
  }

  static void verifySentParams(
      Connection connection, String path, ImmutableMap<String, String> parameterMap)
          throws Exception {
    verify(connection).send(
        eq(path),
        eq(parameterMap),
        eq(MediaType.PLAIN_TEXT_UTF_8),
        eq(new byte[0]));
  }
}
