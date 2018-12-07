// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.reporting.spec11;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import google.registry.beam.spec11.Spec11Pipeline;
import google.registry.beam.spec11.ThreatMatch;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.inject.Inject;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Parser to retrieve which registrar-threat matches we should notify via email */
public class Spec11RegistrarThreatMatchesParser {

  private final LocalDate date;
  private final GcsUtils gcsUtils;
  private final String reportingBucket;

  @Inject
  public Spec11RegistrarThreatMatchesParser(
      LocalDate date, GcsUtils gcsUtils, @Config("reportingBucket") String reportingBucket) {
    this.date = date;
    this.gcsUtils = gcsUtils;
    this.reportingBucket = reportingBucket;
  }

  /** Gets the list of registrar:set-of-threat-match pairings from the file in GCS. */
  public ImmutableList<RegistrarThreatMatches> getRegistrarThreatMatches()
      throws IOException, JSONException {
    // TODO(b/120078223): this should only be the diff of this run and the prior run.
    GcsFilename spec11ReportFilename =
        new GcsFilename(reportingBucket, Spec11Pipeline.getSpec11ReportFilePath(date));
    ImmutableList.Builder<RegistrarThreatMatches> builder = ImmutableList.builder();
    try (InputStream in = gcsUtils.openInputStream(spec11ReportFilename)) {
      ImmutableList<String> reportLines =
          ImmutableList.copyOf(CharStreams.toString(new InputStreamReader(in, UTF_8)).split("\n"));
      // Iterate from 1 to size() to skip the header at line 0.
      for (int i = 1; i < reportLines.size(); i++) {
        builder.add(parseRegistrarThreatMatch(reportLines.get(i)));
      }
      return builder.build();
    }
  }

  private RegistrarThreatMatches parseRegistrarThreatMatch(String line) throws JSONException {
    JSONObject reportJSON = new JSONObject(line);
    String registrarEmail = reportJSON.getString(Spec11Pipeline.REGISTRAR_EMAIL_FIELD);
    JSONArray threatMatchesArray = reportJSON.getJSONArray(Spec11Pipeline.THREAT_MATCHES_FIELD);
    ImmutableList.Builder<ThreatMatch> threatMatches = ImmutableList.builder();
    for (int i = 0; i < threatMatchesArray.length(); i++) {
      threatMatches.add(ThreatMatch.fromJSON(threatMatchesArray.getJSONObject(i)));
    }
    return RegistrarThreatMatches.create(registrarEmail, threatMatches.build());
  }
}
