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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.googlecode.objectify.Key;
import google.registry.model.domain.LrpToken;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.DeterministicStringGenerator;
import google.registry.testing.DeterministicStringGenerator.Rule;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link CreateLrpTokensCommand}. */
public class CreateLrpTokensCommandTest extends CommandTestCase<CreateLrpTokensCommand> {

  DeterministicStringGenerator stringGenerator =
      new DeterministicStringGenerator("abcdefghijklmnopqrstuvwxyz");
  File assigneeFile;
  String assigneeFilePath;

  @Before
  public void init() throws IOException {
    assigneeFile = tmpDir.newFile("lrp_assignees.txt");
    assigneeFilePath = assigneeFile.getPath();
    command.stringGenerator = stringGenerator;
    createTld("tld");
  }

  @Test
  public void testSuccess_oneAssignee() throws Exception {
    runCommand("--assignee=domain.tld", "--tlds=tld");
    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain.tld", ImmutableSet.of("tld"), null));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_oneAssignee_tokenCollision() throws Exception {
    LrpToken existingToken = persistResource(new LrpToken.Builder()
        .setToken("LRP_abcdefghijklmnop")
        .setAssignee("otherdomain.tld")
        .setValidTlds(ImmutableSet.of("tld"))
        .build());
    runCommand("--assignee=domain.tld", "--tlds=tld");
    assertLrpTokens(
        existingToken,
        createToken("LRP_qrstuvwxyzabcdef", "domain.tld", ImmutableSet.of("tld"), null));
    assertInStdout("domain.tld,LRP_qrstuvwxyzabcdef");
  }

  @Test
  public void testSuccess_oneAssignee_byFile() throws Exception {
    Files.write("domain.tld", assigneeFile, UTF_8);
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");
    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain.tld", ImmutableSet.of("tld"), null));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_emptyFile() throws Exception {
    Files.write("", assigneeFile, UTF_8);
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");
    assertLrpTokens(); // no tokens exist
    assertThat(getStdoutAsString()).isEmpty();
  }

  @Test
  public void testSuccess_multipleAssignees_byFile() throws Exception {
    Files.write("domain1.tld\ndomain2.tld\ndomain3.tld", assigneeFile, UTF_8);
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");

    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain1.tld", ImmutableSet.of("tld"), null),
        createToken("LRP_qrstuvwxyzabcdef", "domain2.tld", ImmutableSet.of("tld"), null),
        createToken("LRP_ghijklmnopqrstuv", "domain3.tld", ImmutableSet.of("tld"), null));

    assertInStdout("domain1.tld,LRP_abcdefghijklmnop");
    assertInStdout("domain2.tld,LRP_qrstuvwxyzabcdef");
    assertInStdout("domain3.tld,LRP_ghijklmnopqrstuv");
  }

  @Test
  public void testSuccess_multipleAssignees_byFile_ignoreBlankLine() throws Exception {
    Files.write("domain1.tld\n\ndomain2.tld", assigneeFile, UTF_8);
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");
    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain1.tld", ImmutableSet.of("tld"), null),
        // Second deterministic token (LRP_qrstuvwxyzabcdef) still consumed but not assigned
        createToken("LRP_ghijklmnopqrstuv", "domain2.tld", ImmutableSet.of("tld"), null));
    assertInStdout("domain1.tld,LRP_abcdefghijklmnop");
    assertInStdout("domain2.tld,LRP_ghijklmnopqrstuv");
  }

  @Test
  public void testSuccess_largeFile() throws Exception {
    int numberOfTokens = 67;
    LrpToken[] expectedTokens = new LrpToken[numberOfTokens];
    // Prepend a counter to avoid collisions, 16-char alphabet will always generate the same string.
    stringGenerator =
        new DeterministicStringGenerator("abcdefghijklmnop", Rule.PREPEND_COUNTER);
    command.stringGenerator = stringGenerator;
    StringBuilder assigneeFileBuilder = new StringBuilder();
    for (int i = 0; i < numberOfTokens; i++) {
      assigneeFileBuilder.append(String.format("domain%d.tld\n", i));
      expectedTokens[i] =
          createToken(
              String.format("LRP_%04d_abcdefghijklmnop", i),
              String.format("domain%d.tld", i),
              ImmutableSet.of("tld"),
              null);
    }
    Files.write(assigneeFileBuilder, assigneeFile, UTF_8);
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");
    assertLrpTokens(expectedTokens);
    for (int i = 0; i < numberOfTokens; i++) {
      assertInStdout(String.format("domain%d.tld,LRP_%04d_abcdefghijklmnop", i, i));
    }
  }

  @Test
  public void testFailure_missingAssigneeOrFile() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Exactly one of either assignee or filename must be specified.");
    runCommand("--tlds=tld");
  }

  @Test
  public void testFailure_bothAssigneeAndFile() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Exactly one of either assignee or filename must be specified.");
    runCommand("--assignee=domain.tld", "--tlds=tld", "--input=" + assigneeFilePath);
  }

  @Test
  public void testFailure_badTld() throws Exception {
    thrown.expect(IllegalArgumentException.class, "TLD foo does not exist");
    runCommand("--assignee=domain.tld", "--tlds=foo");
  }

  private void assertLrpTokens(LrpToken... expected) throws Exception {
    // Using ImmutableObject comparison here is tricky because updateTimestamp is not set on the
    // expected LrpToken objects and will cause the assert to fail.
    Iterable<LrpToken> actual = ofy().load().type(LrpToken.class);
    ImmutableMap.Builder<String, LrpToken> actualTokenMapBuilder = new ImmutableMap.Builder<>();
    for (LrpToken token : actual) {
      actualTokenMapBuilder.put(token.getToken(), token);
    }
    ImmutableMap<String, LrpToken> actualTokenMap = actualTokenMapBuilder.build();
    assertThat(actualTokenMap).hasSize(expected.length);
    for (LrpToken expectedToken : expected) {
      LrpToken match = actualTokenMap.get(expectedToken.getToken());
      assertThat(match).isNotNull();
      assertThat(match.getAssignee()).isEqualTo(expectedToken.getAssignee());
      assertThat(match.getValidTlds()).containsExactlyElementsIn(expectedToken.getValidTlds());
      assertThat(match.getRedemptionHistoryEntry())
          .isEqualTo(expectedToken.getRedemptionHistoryEntry());
    }
  }

  private LrpToken createToken(
      String token,
      String assignee,
      Set<String> validTlds,
      @Nullable Key<HistoryEntry> redemptionHistoryEntry) {
    LrpToken.Builder tokenBuilder = new LrpToken.Builder()
        .setAssignee(assignee)
        .setValidTlds(validTlds)
        .setToken(token);
    if (redemptionHistoryEntry != null) {
      tokenBuilder.setRedemptionHistoryEntry(redemptionHistoryEntry);
    }
    return tokenBuilder.build();
  }
}
