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

package google.registry.tools;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.partition;
import static com.google.common.collect.Streams.stream;
import static google.registry.model.domain.token.AllocationToken.TokenType.SINGLE_USE;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;
import google.registry.model.domain.token.AllocationToken;
import java.util.List;

/**
 * Command to delete unused {@link AllocationToken}s.
 *
 * <p>Allocation tokens that have been redeemed cannot be deleted. To delete a single allocation
 * token, specify the entire token as the prefix.
 */
@Parameters(
    separators = " =",
    commandDescription = "Deletes the unused AllocationTokens with a given prefix.")
final class DeleteAllocationTokensCommand extends ConfirmingCommand
    implements CommandWithRemoteApi {

  @Parameter(
      names = {"-p", "--prefix"},
      description = "Allocation token prefix; if blank, deletes all unused tokens",
      required = true)
  private String prefix;

  @Parameter(
      names = {"--with_domains"},
      description = "Allow deletion of allocation tokens with specified domains; defaults to false")
  boolean withDomains;

  @Parameter(
      names = {"--dry_run"},
      description = "Do not actually delete the tokens; defaults to false")
  boolean dryRun;

  private static final int BATCH_SIZE = 20;
  private static final Joiner JOINER = Joiner.on(", ");

  private ImmutableSet<Key<AllocationToken>> tokensToDelete;

  @Override
  public void init() {
    Query<AllocationToken> query =
        ofy().load().type(AllocationToken.class).filter("redemptionHistoryEntry", null);
    tokensToDelete =
        query.keys().list().stream()
            .filter(key -> key.getName().startsWith(prefix))
            .collect(toImmutableSet());
  }

  @Override
  protected String prompt() {
    return String.format(
        "Found %d unused tokens starting with '%s' to delete.", tokensToDelete.size(), prefix);
  }

  @Override
  protected String execute() {
    long numDeleted =
        stream(partition(tokensToDelete, BATCH_SIZE))
            .mapToLong(batch -> ofy().transact(() -> deleteBatch(batch)))
            .sum();
    return String.format("Deleted %d tokens in total.", numDeleted);
  }

  /** Deletes a (filtered) batch of AllocationTokens and returns how many were deleted. */
  private long deleteBatch(List<Key<AllocationToken>> batch) {
    // Load the tokens in the same transaction as they are deleted to verify they weren't redeemed
    // since the query ran. This also filters out per-domain tokens if they're not to be deleted.
    ImmutableSet<AllocationToken> tokensToDelete =
        ofy().load().keys(batch).values().stream()
            .filter(t -> withDomains || !t.getDomainName().isPresent())
            .filter(t -> SINGLE_USE.equals(t.getTokenType()))
            .filter(t -> !t.isRedeemed())
            .collect(toImmutableSet());
    if (!dryRun) {
      ofy().delete().entities(tokensToDelete);
    }
    System.out.printf(
        "%s tokens: %s\n",
        dryRun ? "Would delete" : "Deleted",
        JOINER.join(
            tokensToDelete.stream()
                .map(AllocationToken::getToken)
                .sorted()
                .collect(toImmutableSet())));
    return tokensToDelete.size();
  }
}
