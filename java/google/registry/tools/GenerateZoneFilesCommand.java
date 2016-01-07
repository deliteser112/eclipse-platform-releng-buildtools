// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools;

import static google.registry.model.registry.Registries.assertTldExists;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Duration.standardMinutes;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableMap;
import google.registry.tools.params.DateTimeParameter;
import google.registry.tools.server.GenerateZoneFilesAction;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;

/** Command to generate zone files. */
@Parameters(separators = " =", commandDescription = "Generate zone files")
final class GenerateZoneFilesCommand implements ServerSideCommand {

  @Parameter(
      description = "A comma-separated list of TLD to generate zone files for",
      required = true)
  private List<String> mainParameters;

  // Default to latest midnight that's at least 2 minutes ago.
  @Parameter(
      names = "--export_time",
      description = "The (midnight UTC) time to generate the file for (defaults to last midnight).",
      validateWith = DateTimeParameter.class)
  private DateTime exportTime = DateTime.now(UTC).minus(standardMinutes(2)).withTimeAtStartOfDay();

  private Connection connection;

  @Override
  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  @Override
  public void run() throws IOException {
    for (String tld : mainParameters) {
      assertTldExists(tld);
    }
    ImmutableMap<String, Object> params = ImmutableMap.of(
        "tlds", mainParameters,
        "exportTime", exportTime.toString());
    Map<String, Object> response = connection.sendJson(GenerateZoneFilesAction.PATH, params);
    System.out.printf(
        "Job started at %s%s\n",
        connection.getServerUrl(),
        response.get("jobPath"));
    System.out.println("Output files:");
    @SuppressWarnings("unchecked")
    List<String> filenames = (List<String>) response.get("filenames");
    for (String filename : filenames) {
      System.out.println(filename);
    }
  }
}
