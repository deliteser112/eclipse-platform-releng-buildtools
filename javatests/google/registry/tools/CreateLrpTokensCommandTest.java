// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.google.appengine.tools.remoteapi.RemoteApiException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.googlecode.objectify.Key;
import google.registry.model.domain.LrpTokenEntity;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.DeterministicStringGenerator;
import google.registry.testing.DeterministicStringGenerator.Rule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeSleeper;
import google.registry.util.Retrier;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import javax.annotation.Nullable;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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
    command.retrier =
        new Retrier(new FakeSleeper(new FakeClock(DateTime.parse("2000-01-01TZ"))), 3);
    createTld("tld");
  }

  @Test
  public void testSuccess_oneAssignee() throws Exception {
    runCommand("--assignee=domain.tld", "--tlds=tld");
    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain.tld", ImmutableSet.of("tld"), null, null));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_oneAssignee_retry() throws Exception {
    CreateLrpTokensCommand spyCommand = spy(command);
    RemoteApiException fakeException = new RemoteApiException("foo", "foo", "foo", new Exception());
    doThrow(fakeException)
        .doThrow(fakeException)
        .doCallRealMethod()
        .when(spyCommand)
        .saveTokens(Mockito.<ImmutableSet<LrpTokenEntity>>any());
    runCommand("--assignee=domain.tld", "--tlds=tld");
    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain.tld", ImmutableSet.of("tld"), null, null));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_oneAssignee_withMetadata() throws Exception {
    runCommand("--assignee=domain.tld", "--tlds=tld", "--metadata=key=foo,key2=bar");
    assertLrpTokens(
        createToken(
            "LRP_abcdefghijklmnop",
            "domain.tld",
            ImmutableSet.of("tld"),
            null,
            ImmutableMap.of("key", "foo", "key2", "bar")));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_oneAssignee_tokenCollision() throws Exception {
    LrpTokenEntity existingToken = persistResource(new LrpTokenEntity.Builder()
        .setToken("LRP_abcdefghijklmnop")
        .setAssignee("otherdomain.tld")
        .setValidTlds(ImmutableSet.of("tld"))
        .build());
    runCommand("--assignee=domain.tld", "--tlds=tld");
    assertLrpTokens(
        existingToken,
        createToken("LRP_qrstuvwxyzabcdef", "domain.tld", ImmutableSet.of("tld"), null, null));
    assertInStdout("domain.tld,LRP_qrstuvwxyzabcdef");
  }

  @Test
  public void testSuccess_oneAssignee_byFile() throws Exception {
    Files.asCharSink(assigneeFile, UTF_8).write("domain.tld");
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");
    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain.tld", ImmutableSet.of("tld"), null, null));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_oneAssignee_byFile_withMetadata() throws Exception {
    Files.asCharSink(assigneeFile, UTF_8).write("domain.tld,foo,bar");
    runCommand("--input=" + assigneeFilePath, "--tlds=tld", "--metadata_columns=key=1,key2=2");
    assertLrpTokens(
        createToken(
            "LRP_abcdefghijklmnop",
            "domain.tld",
            ImmutableSet.of("tld"),
            null,
            ImmutableMap.of("key", "foo", "key2", "bar")));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_oneAssignee_byFile_withMetadata_quotedString() throws Exception {
    Files.asCharSink(assigneeFile, UTF_8).write("domain.tld,\"foo,foo\",bar");
    runCommand("--input=" + assigneeFilePath, "--tlds=tld", "--metadata_columns=key=1,key2=2");
    assertLrpTokens(
        createToken(
            "LRP_abcdefghijklmnop",
            "domain.tld",
            ImmutableSet.of("tld"),
            null,
            ImmutableMap.of("key", "foo,foo", "key2", "bar")));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_oneAssignee_byFile_withMetadata_twoQuotedStrings() throws Exception {
    Files.asCharSink(assigneeFile, UTF_8).write("domain.tld,\"foo,foo\",\"bar,bar\"");
    runCommand("--input=" + assigneeFilePath, "--tlds=tld", "--metadata_columns=key=1,key2=2");
    assertLrpTokens(
        createToken(
            "LRP_abcdefghijklmnop",
            "domain.tld",
            ImmutableSet.of("tld"),
            null,
            ImmutableMap.of("key", "foo,foo", "key2", "bar,bar")));
    assertInStdout("domain.tld,LRP_abcdefghijklmnop");
  }

  @Test
  public void testSuccess_emptyFile() throws Exception {
    Files.asCharSink(assigneeFile, UTF_8).write("");
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");
    assertLrpTokens(); // no tokens exist
    assertThat(getStdoutAsString()).isEmpty();
  }

  @Test
  public void testSuccess_multipleAssignees_byFile() throws Exception {
    Files.asCharSink(assigneeFile, UTF_8).write("domain1.tld\ndomain2.tld\ndomain3.tld");
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");

    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain1.tld", ImmutableSet.of("tld"), null, null),
        createToken("LRP_qrstuvwxyzabcdef", "domain2.tld", ImmutableSet.of("tld"), null, null),
        createToken("LRP_ghijklmnopqrstuv", "domain3.tld", ImmutableSet.of("tld"), null, null));

    assertInStdout("domain1.tld,LRP_abcdefghijklmnop");
    assertInStdout("domain2.tld,LRP_qrstuvwxyzabcdef");
    assertInStdout("domain3.tld,LRP_ghijklmnopqrstuv");
  }

  @Test
  public void testSuccess_multipleAssignees_byFile_ignoreBlankLine() throws Exception {
    Files.asCharSink(assigneeFile, UTF_8).write("domain1.tld\n\ndomain2.tld");
    runCommand("--input=" + assigneeFilePath, "--tlds=tld");
    assertLrpTokens(
        createToken("LRP_abcdefghijklmnop", "domain1.tld", ImmutableSet.of("tld"), null, null),
        // Second deterministic token (LRP_qrstuvwxyzabcdef) still consumed but not assigned
        createToken("LRP_ghijklmnopqrstuv", "domain2.tld", ImmutableSet.of("tld"), null, null));
    assertInStdout("domain1.tld,LRP_abcdefghijklmnop");
    assertInStdout("domain2.tld,LRP_ghijklmnopqrstuv");
  }

  @Test
  public void testSuccess_largeFile_withMetadata() throws Exception {
    int numberOfTokens = 67;
    LrpTokenEntity[] expectedTokens = new LrpTokenEntity[numberOfTokens];
    // Prepend a counter to avoid collisions, 16-char alphabet will always generate the same string.
    stringGenerator =
        new DeterministicStringGenerator("abcdefghijklmnop", Rule.PREPEND_COUNTER);
    command.stringGenerator = stringGenerator;
    StringBuilder assigneeFileBuilder = new StringBuilder();
    for (int i = 0; i < numberOfTokens; i++) {
      assigneeFileBuilder.append(String.format("domain%d.tld,%d,%d\n", i, i * 2, i * 3));
      expectedTokens[i] =
          createToken(
              String.format("LRP_%04d_abcdefghijklmnop", i),
              String.format("domain%d.tld", i),
              ImmutableSet.of("tld"),
              null,
              ImmutableMap.of("key", Integer.toString(i * 2), "key2", Integer.toString(i * 3)));
    }
    Files.asCharSink(assigneeFile, UTF_8).write(assigneeFileBuilder);
    runCommand("--input=" + assigneeFilePath, "--tlds=tld", "--metadata_columns=key=1,key2=2");
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
  public void testFailure_bothMetadataAndFile() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Metadata cannot be specified along with a filename.");
    runCommand("--tlds=tld", "--input=" + assigneeFilePath, "--metadata=key=foo");
  }

  @Test
  public void testFailure_bothAssigneeAndMetadataColumns() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Metadata columns cannot be specified along with an assignee.");
    runCommand("--assignee=domain.tld", "--tlds=tld", "--metadata_columns=foo=1");
  }

  @Test
  public void testFailure_badTld() throws Exception {
    thrown.expect(IllegalArgumentException.class, "TLD foo does not exist");
    runCommand("--assignee=domain.tld", "--tlds=foo");
  }

  @Test
  public void testFailure_oneAssignee_byFile_insufficientMetadata() throws Exception {
    Files.asCharSink(assigneeFile, UTF_8).write("domain.tld,foo");
    thrown.expect(IllegalArgumentException.class,
        "Entry for domain.tld does not have a value for key2 (index 2)");
    runCommand("--input=" + assigneeFilePath, "--tlds=tld", "--metadata_columns=key=1,key2=2");
  }

  private void assertLrpTokens(LrpTokenEntity... expected) throws Exception {
    // Using ImmutableObject comparison here is tricky because updateTimestamp is not set on the
    // expected LrpToken objects and will cause the assert to fail.
    Iterable<LrpTokenEntity> actual = ofy().load().type(LrpTokenEntity.class);
    ImmutableMap.Builder<String, LrpTokenEntity> actualTokenMapBuilder =
        new ImmutableMap.Builder<>();
    for (LrpTokenEntity token : actual) {
      actualTokenMapBuilder.put(token.getToken(), token);
    }
    ImmutableMap<String, LrpTokenEntity> actualTokenMap = actualTokenMapBuilder.build();
    assertThat(actualTokenMap).hasSize(expected.length);
    for (LrpTokenEntity expectedToken : expected) {
      LrpTokenEntity match = actualTokenMap.get(expectedToken.getToken());
      assertThat(match).isNotNull();
      assertThat(match.getAssignee()).isEqualTo(expectedToken.getAssignee());
      assertThat(match.getValidTlds()).containsExactlyElementsIn(expectedToken.getValidTlds());
      assertThat(match.getRedemptionHistoryEntry())
          .isEqualTo(expectedToken.getRedemptionHistoryEntry());
      assertThat(match.getMetadata()).containsExactlyEntriesIn(expectedToken.getMetadata());
    }
  }

  private LrpTokenEntity createToken(
      String token,
      String assignee,
      Set<String> validTlds,
      @Nullable Key<HistoryEntry> redemptionHistoryEntry,
      @Nullable ImmutableMap<String, String> metadata) {
    LrpTokenEntity.Builder tokenBuilder = new LrpTokenEntity.Builder()
        .setAssignee(assignee)
        .setValidTlds(validTlds)
        .setToken(token);
    if (redemptionHistoryEntry != null) {
      tokenBuilder.setRedemptionHistoryEntry(redemptionHistoryEntry);
    }
    if (metadata != null) {
      tokenBuilder.setMetadata(metadata);
    }
    return tokenBuilder.build();
  }
}
