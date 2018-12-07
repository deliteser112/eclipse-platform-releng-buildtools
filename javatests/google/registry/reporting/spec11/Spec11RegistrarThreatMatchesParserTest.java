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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.beam.spec11.ThreatMatch;
import google.registry.gcs.GcsUtils;
import google.registry.testing.TestDataHelper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Spec11RegistrarThreatMatchesParser}. */
@RunWith(JUnit4.class)
public class Spec11RegistrarThreatMatchesParserTest {

  private final GcsUtils gcsUtils = mock(GcsUtils.class);
  private final Spec11RegistrarThreatMatchesParser parser =
      new Spec11RegistrarThreatMatchesParser(new LocalDate(2018, 7, 21), gcsUtils, "test-bucket");

  @Before
  public void setUp() {
    when(gcsUtils.openInputStream(
            new GcsFilename(
                "test-bucket", "icann/spec11/2018-07/SPEC11_MONTHLY_REPORT_2018-07-21")))
        .thenAnswer(
            (args) ->
                new ByteArrayInputStream(
                    loadFile("spec11_fake_report").getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void testSuccess_retrievesReport() throws Exception {
    List<RegistrarThreatMatches> matches = parser.getRegistrarThreatMatches();
    assertThat(matches).isEqualTo(sampleThreatMatches());
  }

  /** Returns a {@link String} from a file in the {@code spec11/testdata/} directory. */
  public static String loadFile(String filename) {
    return TestDataHelper.loadFile(Spec11EmailUtils.class, filename);
  }

  /** The expected contents of the sample spec11 report file */
  public static ImmutableList<RegistrarThreatMatches> sampleThreatMatches() throws JSONException {
    return ImmutableList.of(
        RegistrarThreatMatches.create(
            "a@fake.com",
            ImmutableList.of(
                ThreatMatch.fromJSON(
                    new JSONObject(
                        ImmutableMap.of(
                            "threatType", "MALWARE",
                            "platformType", "ANY_PLATFORM",
                            "threatEntryMetadata", "NONE",
                            "fullyQualifiedDomainName", "a.com"))))),
        RegistrarThreatMatches.create(
            "b@fake.com",
            ImmutableList.of(
                ThreatMatch.fromJSON(
                    new JSONObject(
                        ImmutableMap.of(
                            "threatType", "MALWARE",
                            "platformType", "ANY_PLATFORM",
                            "threatEntryMetadata", "NONE",
                            "fullyQualifiedDomainName", "b.com"))),
                ThreatMatch.fromJSON(
                    new JSONObject(
                        ImmutableMap.of(
                            "threatType", "MALWARE",
                            "platformType", "ANY_PLATFORM",
                            "threatEntryMetadata", "NONE",
                            "fullyQualifiedDomainName", "c.com"))))));
  }
}
