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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Sets.difference;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.util.TokenUtils.TokenType.LRP;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.appengine.tools.remoteapi.RemoteApiException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.io.LineReader;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import google.registry.model.domain.LrpTokenEntity;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.KeyValueMapParameter.StringToIntegerMap;
import google.registry.tools.params.KeyValueMapParameter.StringToStringMap;
import google.registry.tools.params.PathParameter;
import google.registry.util.NonFinalForTesting;
import google.registry.util.Retrier;
import google.registry.util.StringGenerator;
import google.registry.util.TokenUtils;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;

/**
 * Command to create one or more LRP tokens, given assignee(s) as either a parameter or a text file.
 */
@NonFinalForTesting
@Parameters(
    separators = " =",
    commandDescription = "Create an LRP token for a given assignee (using -a) or import a text"
        + " file of assignees for bulk token creation (using -i). Assignee/token pairs are printed"
        + " to stdout, and should be piped to a file for distribution to assignees or for cleanup"
        + " in the event of a command interruption.")
public class CreateLrpTokensCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-a", "--assignee"},
      description = "LRP token assignee")
  private String assignee;

  @Parameter(
      names = {"-t", "--tlds"},
      description = "Comma-delimited list of TLDs that the tokens to create will be valid on",
      required = true)
  private String tlds;

  @Parameter(
      names = {"-i", "--input"},
      description = "Filename containing a list of assignees, newline-delimited",
      validateWith = PathParameter.InputFile.class)
  private Path assigneesFile;

  @Parameter(
      names = {"-m", "--metadata"},
      description = "Token metadata key-value pairs (formatted as key=value[,key=value...]). Used"
          + " only in conjunction with -a/--assignee when creating a single token.",
      converter = StringToStringMap.class,
      validateWith = StringToStringMap.class)
  private ImmutableMap<String, String> metadata;

  @Parameter(
      names = {"-c", "--metadata_columns"},
      description = "Token metadata columns (formatted as key=index[,key=index...], columns are"
          + " zero-indexed). Used only in conjunction with -i/--input to map additional fields in"
          + " the CSV file to metadata stored on the LRP token. The index corresponds to the column"
          + " number in the CSV file (where the assignee is assigned column 0).",
      converter = StringToIntegerMap.class,
      validateWith = StringToIntegerMap.class)
  private ImmutableMap<String, Integer> metadataColumns;

  @Inject StringGenerator stringGenerator;
  @Inject Retrier retrier;

  private static final int BATCH_SIZE = 20;
  
  // Ensures that all of the double quotes to the right of a comma are balanced. In a well-formed
  // CSV line, there can be no leading double quote preceding the comma.
  private static final String COMMA_EXCEPT_WHEN_QUOTED_REGEX =
      ",(?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)";

  @Override
  public void run() throws Exception {
    checkArgument(
        (assignee == null) == (assigneesFile != null),
        "Exactly one of either assignee or filename must be specified.");
    checkArgument(
        (assigneesFile == null) || (metadata == null),
        "Metadata cannot be specified along with a filename.");
    checkArgument(
        (assignee == null) || (metadataColumns == null),
        "Metadata columns cannot be specified along with an assignee.");
    final Set<String> validTlds = ImmutableSet.copyOf(Splitter.on(',').split(tlds));
    for (String tld : validTlds) {
      assertTldExists(tld);
    }

    LineReader reader = new LineReader(
        (assigneesFile != null)
            ? Files.newReader(assigneesFile.toFile(), UTF_8)
            : new StringReader(assignee));

    String line = null;
    do {
      ImmutableSet.Builder<LrpTokenEntity> tokensToSaveBuilder = new ImmutableSet.Builder<>();
      for (String token : generateTokens(BATCH_SIZE)) {
        line = reader.readLine();
        if (!isNullOrEmpty(line)) {
          ImmutableList<String> values =
              ImmutableList.copyOf(
                  Splitter.onPattern(COMMA_EXCEPT_WHEN_QUOTED_REGEX)
                      // Results should not be surrounded in double quotes.
                      .trimResults(CharMatcher.is('\"'))
                      .split(line));
          LrpTokenEntity.Builder tokenBuilder = new LrpTokenEntity.Builder()
              .setAssignee(values.get(0))
              .setToken(token)
              .setValidTlds(validTlds);
          if (metadata != null) {
            tokenBuilder.setMetadata(metadata);
          } else if (metadataColumns != null) {
            ImmutableMap.Builder<String, String> metadataBuilder = ImmutableMap.builder();
            for (ImmutableMap.Entry<String, Integer> entry : metadataColumns.entrySet()) {
              checkArgument(
                  values.size() > entry.getValue(),
                  "Entry for %s does not have a value for %s (index %s)",
                  values.get(0),
                  entry.getKey(),
                  entry.getValue());
              metadataBuilder.put(entry.getKey(), values.get(entry.getValue()));
            }
            tokenBuilder.setMetadata(metadataBuilder.build());
          }
          tokensToSaveBuilder.add(tokenBuilder.build());
        }
      }
      final ImmutableSet<LrpTokenEntity> tokensToSave = tokensToSaveBuilder.build();
      // Wrap in a retrier to deal with transient 404 errors (thrown as RemoteApiExceptions).
      retrier.callWithRetry(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          saveTokens(tokensToSave);
          return null;
        }}, RemoteApiException.class);
    } while (line != null);
  }

  @VisibleForTesting
  void saveTokens(final ImmutableSet<LrpTokenEntity> tokens) {
    Collection<LrpTokenEntity> savedTokens =
        ofy().transact(new Work<Collection<LrpTokenEntity>>() {
          @Override
          public Collection<LrpTokenEntity> run() {
            return ofy().save().entities(tokens).now().values();
          }});
    for (LrpTokenEntity token : savedTokens) {
      System.out.printf("%s,%s%n", token.getAssignee(), token.getToken());
    }
  }

  /**
   * This function generates at MOST {@code count} tokens, after filtering out any token strings
   * that already exist.
   *
   * <p>Note that in the incredibly rare case that all generated tokens already exist, this function
   * may return an empty set.
   */
  private ImmutableSet<String> generateTokens(int count) {
    final ImmutableSet<String> candidates =
        ImmutableSet.copyOf(TokenUtils.createTokens(LRP, stringGenerator, count));
    ImmutableSet<Key<LrpTokenEntity>> existingTokenKeys = FluentIterable.from(candidates)
        .transform(new Function<String, Key<LrpTokenEntity>>() {
          @Override
          public Key<LrpTokenEntity> apply(String input) {
            return Key.create(LrpTokenEntity.class, input);
          }})
        .toSet();
    ImmutableSet<String> existingTokenStrings = FluentIterable
        .from(ofy().load().keys(existingTokenKeys).values())
        .transform(new Function<LrpTokenEntity, String>() {
          @Override
          public String apply(LrpTokenEntity input) {
            return input.getToken();
          }})
        .toSet();
    return ImmutableSet.copyOf(difference(candidates, existingTokenStrings));
  }
}
