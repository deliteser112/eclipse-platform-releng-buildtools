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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Holds domain, nameserver and entity search results.
 * 
 * <p>We need to know not only the list of things we found, but also whether the result set was
 * truncated to the limit. If it is, we must add the ICANN-mandated notice to that effect.
 */
@AutoValue
abstract class RdapSearchResults {
  
  static RdapSearchResults create(ImmutableList<ImmutableMap<String, Object>> jsonList) {
    return create(jsonList, false);
  }
  
  static RdapSearchResults create(
      ImmutableList<ImmutableMap<String, Object>> jsonList, boolean isTruncated) {
    return new AutoValue_RdapSearchResults(jsonList, isTruncated);
  }

  /** List of JSON result object representations. */
  abstract ImmutableList<ImmutableMap<String, Object>> jsonList();
  
  /** True if the result set was truncated to the maximum size limit. */
  abstract boolean isTruncated();
}
