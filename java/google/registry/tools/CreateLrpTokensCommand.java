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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Sets.difference;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.assertTldExists;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.io.LineReader;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import google.registry.model.domain.LrpToken;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import javax.inject.Inject;

/**
 * Command to create one or more LRP tokens, given assignee(s) as either a parameter or a text file.
 */
@Parameters(
    separators = " =",
    commandDescription = "Create an LRP token for a given assignee (using -a) or import a text"
        + " file of assignees for bulk token creation (using -i). Assignee/token pairs are printed"
        + " to stdout, and should be piped to a file for distribution to assignees or for cleanup"
        + " in the event of a command interruption.")
public final class CreateLrpTokensCommand implements RemoteApiCommand, GtechCommand {

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

  @Inject StringGenerator stringGenerator;

  private static final int TOKEN_LENGTH = 16;
  private static final int BATCH_SIZE = 20;

  @Override
  public void run() throws Exception {
    checkArgument(
        (assignee == null) == (assigneesFile != null),
        "Exactly one of either assignee or filename must be specified.");
    final Set<String> validTlds = ImmutableSet.copyOf(Splitter.on(',').split(tlds));
    for (String tld : validTlds) {
      assertTldExists(tld);
    }

    LineReader reader = new LineReader(
        (assignee == null)
            ? Files.newReader(assigneesFile.toFile(), UTF_8)
            : new StringReader(assignee));

    String line = null;
    do {
      ImmutableSet.Builder<LrpToken> tokensToSave = new ImmutableSet.Builder<>();
      for (String token : generateTokens(BATCH_SIZE)) {
        line = reader.readLine();
        if (!isNullOrEmpty(line)) {
          tokensToSave.add(new LrpToken.Builder()
              .setAssignee(line)
              .setToken(token)
              .setValidTlds(validTlds)
              .build());
        }
      }
      saveTokens(tokensToSave.build());
    } while (line != null);
  }

  private void saveTokens(final ImmutableSet<LrpToken> tokens) {
    Collection<LrpToken> savedTokens = ofy().transact(new Work<Collection<LrpToken>>() {
      @Override
      public Collection<LrpToken> run() {
        return ofy().save().entities(tokens).now().values();
      }});
    for (LrpToken token : savedTokens) {
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
        ImmutableSet.copyOf(stringGenerator.createStrings(TOKEN_LENGTH, count));
    ImmutableSet<Key<LrpToken>> existingTokenKeys = FluentIterable.from(candidates)
        .transform(new Function<String, Key<LrpToken>>() {
          @Override
          public Key<LrpToken> apply(String input) {
            return Key.create(LrpToken.class, input);
          }})
        .toSet();
    ImmutableSet<String> existingTokenStrings = FluentIterable
        .from(ofy().load().keys(existingTokenKeys).values())
        .transform(new Function<LrpToken, String>() {
          @Override
          public String apply(LrpToken input) {
            return input.getToken();
          }})
        .toSet();
    return ImmutableSet.copyOf(difference(candidates, existingTokenStrings));
  }
}
