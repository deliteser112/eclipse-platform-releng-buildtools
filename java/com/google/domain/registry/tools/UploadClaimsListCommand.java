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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.domain.registry.model.tmch.ClaimsListShard;
import com.google.domain.registry.tmch.ClaimsListParser;
import com.google.domain.registry.tools.Command.RemoteApiCommand;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A command to upload a {@link ClaimsListShard}. */
@Parameters(separators = " =", commandDescription = "Manually upload a new claims list file")
final class UploadClaimsListCommand extends ConfirmingCommand implements RemoteApiCommand {

  @Parameter(description = "Claims list filename")
  private List<String> mainParameters = new ArrayList<>();

  private String claimsListFilename;

  private ClaimsListShard claimsList;

  @Override
  protected void init() throws IOException {
    checkArgument(mainParameters.size() == 1,
        "Expected a single argument with the claims list filename. Actual: %s",
        Joiner.on(' ').join(mainParameters));
    claimsListFilename = mainParameters.get(0);
    claimsList = ClaimsListParser.parse(
        Files.asCharSource(new File(claimsListFilename), US_ASCII).readLines());
  }

  @Override
  protected String prompt() throws Exception {
    return String.format("\nNew claims list:\n%s", claimsList);
  }

  @Override
  public String execute() throws IOException {
    claimsList.save();
    return String.format("Successfully uploaded claims list %s", claimsListFilename);
  }
}
