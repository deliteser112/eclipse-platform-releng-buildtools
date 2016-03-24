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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.domain.registry.model.tmch.ClaimsListShard;
import com.google.domain.registry.tools.Command.RemoteApiCommand;
import com.google.domain.registry.tools.params.PathParameter;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A command to download the current claims list.
 *
 * <p>This is not the original file we fetched from TMCH. It is just a representation of what we
 * are currently storing in datastore.
 */
@Parameters(separators = " =", commandDescription = "Download the current claims list")
final class GetClaimsListCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-o", "--output"},
      description = "Output file.",
      validateWith = PathParameter.OutputFile.class)
  private Path output = Paths.get("/dev/stdout");

  @Override
  public void run() throws Exception {
    ClaimsListShard cl = checkNotNull(ClaimsListShard.get(), "Couldn't load ClaimsList");
    String csv = Joiner.on('\n').withKeyValueSeparator(",").join(cl.getLabelsToKeys()) + "\n";
    Files.write(csv, output.toFile(), UTF_8);
  }
}
