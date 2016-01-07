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

package google.registry.export;

import static google.registry.model.registry.label.ReservationType.UNRESERVED;

import com.google.common.base.Joiner;
import com.googlecode.objectify.Key;
import google.registry.config.RegistryEnvironment;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservedList;
import google.registry.model.registry.label.ReservedList.ReservedListEntry;
import java.util.Set;
import java.util.TreeSet;

/** Container class for exported-related static utility methods. */
public class ExportUtils {

  private ExportUtils() {}

  /** Returns the file contents of the auto-export reserved terms document for the given TLD. */
  public static String exportReservedTerms(Registry registry) {
    StringBuilder termsBuilder =
        new StringBuilder(RegistryEnvironment.get().config().getReservedTermsExportDisclaimer());
    Set<String> reservedTerms = new TreeSet<>();
    for (Key<ReservedList> key : registry.getReservedLists()) {
      ReservedList reservedList = ReservedList.load(key).get();
      if (reservedList.getShouldPublish()) {
        for (ReservedListEntry entry : reservedList.getReservedListEntries().values()) {
          if (entry.getValue() != UNRESERVED) {
            reservedTerms.add(entry.getLabel());
          }
        }
      }
    }
    Joiner.on("\n").appendTo(termsBuilder, reservedTerms);
    return termsBuilder.append("\n").toString();
  }
}
