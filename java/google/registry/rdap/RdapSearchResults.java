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

package google.registry.rdap;

import static google.registry.rdap.RdapIcannStandardInformation.POSSIBLY_INCOMPLETE_NOTICES;
import static google.registry.rdap.RdapIcannStandardInformation.TRUNCATION_NOTICES;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/**
 * Holds domain, nameserver and entity search results.
 *
 * <p>We need to know not only the list of things we found, but also whether the result set was
 * truncated to the limit. If it is, we must add the ICANN-mandated notice to that effect.
 */
@AutoValue
abstract class RdapSearchResults {

  enum IncompletenessWarningType {

    /** Result set is complete. */
    COMPLETE,

    /** Result set has been limited to the maximum size. */
    TRUNCATED,

    /**
     * Result set might be missing data because the first step of a two-step query returned a data
     * set that was limited in size.
     */
    MIGHT_BE_INCOMPLETE
  }

  static RdapSearchResults create(ImmutableList<ImmutableMap<String, Object>> jsonList) {
    return create(jsonList, IncompletenessWarningType.COMPLETE, Optional.empty());
  }

  static RdapSearchResults create(
      ImmutableList<ImmutableMap<String, Object>> jsonList,
      IncompletenessWarningType incompletenessWarningType,
      Optional<String> nextCursor) {
    return new AutoValue_RdapSearchResults(jsonList, incompletenessWarningType, nextCursor);
  }

  /** List of JSON result object representations. */
  abstract ImmutableList<ImmutableMap<String, Object>> jsonList();

  /** Type of warning to display regarding possible incomplete data. */
  abstract IncompletenessWarningType incompletenessWarningType();

  /** Cursor for fetching the next page of results, or empty() if there are no more. */
  abstract Optional<String> nextCursor();

  /** Convenience method to get the appropriate warnings for the incompleteness warning type. */
  ImmutableList<ImmutableMap<String, Object>> getIncompletenessWarnings() {
    if (incompletenessWarningType() == IncompletenessWarningType.TRUNCATED) {
      return TRUNCATION_NOTICES;
    }
    if (incompletenessWarningType() == IncompletenessWarningType.MIGHT_BE_INCOMPLETE) {
      return POSSIBLY_INCOMPLETE_NOTICES;
    }
    return ImmutableList.of();
  }
}
