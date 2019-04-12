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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Queues.newArrayDeque;
import static com.google.common.collect.Sets.difference;
import static google.registry.model.domain.token.AllocationToken.TokenType.SINGLE_USE;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.StringGenerator.DEFAULT_PASSWORD_LENGTH;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.appengine.tools.remoteapi.RemoteApiException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.googlecode.objectify.Key;
import google.registry.model.domain.token.AllocationToken;
import google.registry.util.NonFinalForTesting;
import google.registry.util.Retrier;
import google.registry.util.StringGenerator;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Deque;
import javax.inject.Inject;
import javax.inject.Named;

/** Command to generate and persist {@link AllocationToken}s. */
@Parameters(
    separators = " =",
    commandDescription =
        "Generates and persists the given number of AllocationTokens, "
            + "printing each token to stdout.")
@NonFinalForTesting
class GenerateAllocationTokensCommand implements CommandWithRemoteApi {

  @Parameter(
    names = {"-p", "--prefix"},
    description = "Allocation token prefix; defaults to blank"
  )
  private String prefix = "";

  @Parameter(
    names = {"-n", "--number"},
    description = "The number of tokens to generate"
  )
  private long numTokens;

  @Parameter(
      names = {"-d", "--domain_names_file"},
      description = "A file with a list of newline-delimited domain names to create tokens for"
  )
  private String domainNamesFile;

  @Parameter(
    names = {"-l", "--length"},
    description = "The length of each token, exclusive of the prefix (if specified); defaults to 16"
  )
  private int tokenLength = DEFAULT_PASSWORD_LENGTH;

  @Parameter(
      names = {"--dry_run"},
      description = "Do not actually persist the tokens; defaults to false")
  boolean dryRun;

  @Inject
  @Named("base58StringGenerator")
  StringGenerator stringGenerator;

  @Inject Retrier retrier;

  private static final int BATCH_SIZE = 20;
  private static final Joiner SKIP_NULLS = Joiner.on(',').skipNulls();

  @Override
  public void run() throws IOException {
    checkArgument(
        (numTokens > 0) ^ (domainNamesFile != null),
        "Must specify either --number or --domain_names_file, but not both");

    Deque<String> domainNames;
    if (domainNamesFile == null) {
      domainNames = null;
    } else {
      domainNames =
          newArrayDeque(
              Splitter.on('\n')
                  .omitEmptyStrings()
                  .trimResults()
                  .split(Files.asCharSource(new File(domainNamesFile), UTF_8).read()));
      numTokens = domainNames.size();
    }

    int tokensSaved = 0;
    do {
      ImmutableSet<AllocationToken> tokens =
          generateTokens(BATCH_SIZE)
              .stream()
              .limit(numTokens - tokensSaved)
              .map(
                  t -> {
                    AllocationToken.Builder token = new AllocationToken.Builder().setToken(t);
                    // TODO(b/129471448): allow this to be unlimited-use as well
                    token.setTokenType(SINGLE_USE);
                    if (domainNames != null) {
                      token.setDomainName(domainNames.removeFirst());
                    }
                    return token.build();
                  })
              .collect(toImmutableSet());
      // Wrap in a retrier to deal with transient 404 errors (thrown as RemoteApiExceptions).
      tokensSaved += retrier.callWithRetry(() -> saveTokens(tokens), RemoteApiException.class);
    } while (tokensSaved < numTokens);
  }

  @VisibleForTesting
  int saveTokens(final ImmutableSet<AllocationToken> tokens) {
    Collection<AllocationToken> savedTokens =
        dryRun ? tokens : ofy().transact(() -> ofy().save().entities(tokens).now().values());
    savedTokens.forEach(
        t -> System.out.println(SKIP_NULLS.join(t.getDomainName().orElse(null), t.getToken())));
    return savedTokens.size();
  }

  /**
   * This function generates at MOST {@code count} tokens, filtering out already-existing token
   * strings.
   *
   * <p>Note that in the incredibly rare case that all generated tokens already exist, this function
   * may return an empty set.
   */
  private ImmutableSet<String> generateTokens(int count) {
    ImmutableSet<String> candidates =
        stringGenerator
            .createStrings(tokenLength, count)
            .stream()
            .map(s -> prefix + s)
            .collect(toImmutableSet());
    ImmutableSet<Key<AllocationToken>> existingTokenKeys =
        candidates
            .stream()
            .map(input -> Key.create(AllocationToken.class, input))
            .collect(toImmutableSet());
    ImmutableSet<String> existingTokenStrings =
        ofy()
            .load()
            .keys(existingTokenKeys)
            .values()
            .stream()
            .map(AllocationToken::getToken)
            .collect(toImmutableSet());
    return ImmutableSet.copyOf(difference(candidates, existingTokenStrings));
  }
}
