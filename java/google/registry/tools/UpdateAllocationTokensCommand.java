// Copyright 2019 The Nomulus Authors. All Rights Reserved.
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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.partition;
import static com.google.common.collect.Streams.stream;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.googlecode.objectify.Key;
import google.registry.model.domain.token.AllocationToken;
import google.registry.model.domain.token.AllocationToken.TokenStatus;
import google.registry.tools.params.TransitionListParameter.TokenStatusTransitions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.joda.time.DateTime;

/** Command to update existing {@link AllocationToken}s. */
@Parameters(
    separators = " =",
    commandDescription =
        "Updates AllocationTokens with the given prefix, modifying some subset of their allowed"
            + " client IDs, allowed TLDs, discount fraction, or status transitions")
public final class UpdateAllocationTokensCommand extends ConfirmingCommand
    implements CommandWithRemoteApi {

  @Parameter(
      names = {"-p", "--prefix"},
      description =
          "Update all allocation tokens with this prefix; otherwise use '--tokens' to specify"
              + " exact tokens(s) to update")
  private String prefix;

  @Parameter(
      names = {"--tokens"},
      description =
          "Comma-separated list of tokens to update; otherwise use '--prefix' to update all tokens"
              + " with a given prefix")
  private List<String> tokens;

  @Parameter(
      names = {"--dry_run"},
      description = "Do not actually update the tokens; defaults to false")
  private boolean dryRun;

  @Parameter(
      names = {"--allowed_client_ids"},
      description =
          "Comma-separated list of allowed client IDs. Use the empty string to clear the"
              + " existing list.")
  private List<String> allowedClientIds;

  @Parameter(
      names = {"--allowed_tlds"},
      description =
          "Comma-separated list of allowed TLDs. Use the empty string to clear the"
              + " existing list.")
  private List<String> allowedTlds;

  @Parameter(
      names = {"--discount_fraction"},
      description =
          "A discount off the base price for the first year between 0.0 and 1.0. Default is 0.0,"
              + " i.e. no discount.")
  private Double discountFraction;

  @Parameter(
      names = "--token_status_transitions",
      converter = TokenStatusTransitions.class,
      validateWith = TokenStatusTransitions.class,
      description =
          "Comma-delimited list of token status transitions effective on specific dates, of the"
              + " form <time>=<status>[,<time>=<status>]* where each status represents the status.")
  private ImmutableSortedMap<DateTime, TokenStatus> tokenStatusTransitions;

  private static final int BATCH_SIZE = 20;
  private static final Joiner JOINER = Joiner.on(", ");

  private ImmutableSet<AllocationToken> tokensToSave;

  @Override
  public void init() {
    // A single entry with the empty string means that the user passed an empty argument to the
    // lists, so we should clear them
    if (ImmutableList.of("").equals(allowedClientIds)) {
      allowedClientIds = ImmutableList.of();
    }
    if (ImmutableList.of("").equals(allowedTlds)) {
      allowedTlds = ImmutableList.of();
    }

    ImmutableList<Key<AllocationToken>> keysToUpdate = getKeysToUpdate();
    tokensToSave =
        ofy().load().keys(keysToUpdate).values().stream()
            .collect(toImmutableMap(Function.identity(), this::updateToken))
            .entrySet()
            .stream()
            .filter(entry -> !entry.getKey().equals(entry.getValue())) // only update changed tokens
            .map(Map.Entry::getValue)
            .collect(toImmutableSet());
  }

  @Override
  protected String execute() {
    long numUpdated =
        stream(partition(tokensToSave, BATCH_SIZE))
            .mapToLong(batch -> ofy().transact(() -> saveBatch(batch)))
            .sum();
    return String.format("Updated %d tokens in total.", numUpdated);
  }

  private ImmutableList<Key<AllocationToken>> getKeysToUpdate() {
    checkArgument(
        tokens == null ^ prefix == null, "Must provide one of --tokens or --prefix, not both");
    if (tokens != null) {
      return tokens.stream()
          .map(token -> Key.create(AllocationToken.class, token))
          .collect(toImmutableList());
    } else {
      return ofy().load().type(AllocationToken.class).keys().list().stream()
          .filter(key -> key.getName().startsWith(prefix))
          .collect(toImmutableList());
    }
  }

  private AllocationToken updateToken(AllocationToken original) {
    AllocationToken.Builder builder = original.asBuilder();
    Optional.ofNullable(allowedClientIds)
        .ifPresent(clientIds -> builder.setAllowedClientIds(ImmutableSet.copyOf(clientIds)));
    Optional.ofNullable(allowedTlds)
        .ifPresent(tlds -> builder.setAllowedTlds(ImmutableSet.copyOf(tlds)));
    Optional.ofNullable(discountFraction).ifPresent(builder::setDiscountFraction);
    Optional.ofNullable(tokenStatusTransitions).ifPresent(builder::setTokenStatusTransitions);
    return builder.build();
  }

  private long saveBatch(List<AllocationToken> batch) {
    if (!dryRun) {
      ofy().save().entities(batch);
    }
    System.out.printf(
        "%s tokens: %s\n",
        dryRun ? "Would update" : "Updated",
        JOINER.join(
            tokensToSave.stream()
                .map(AllocationToken::getToken)
                .sorted()
                .collect(toImmutableSet())));
    return batch.size();
  }
}
