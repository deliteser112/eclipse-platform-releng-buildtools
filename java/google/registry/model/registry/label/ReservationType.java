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

package google.registry.model.registry.label;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.common.collect.Ordering;
import java.util.Set;
import javax.annotation.Nullable;

/** Enum describing reservation on a label in a {@link ReservedList} */
public enum ReservationType {

  // We explicitly set the severity, even though we have a checkState that makes it equal to the
  // ordinal, so that no one accidentally reorders these values and changes the sort order. If a
  // label has multiple reservation types, its message is the that of the one with the highest
  // severity.

  NAMESERVER_RESTRICTED("Nameserver restricted", 0),
  ALLOWED_IN_SUNRISE("Reserved for non-sunrise", 1),
  MISTAKEN_PREMIUM("Reserved", 2),
  RESERVED_FOR_ANCHOR_TENANT("Reserved", 3),
  NAME_COLLISION("Cannot be delegated", 4),
  FULLY_BLOCKED("Reserved", 5);

  @Nullable
  private final String messageForCheck;

  ReservationType(@Nullable String messageForCheck, int severity) {
    this.messageForCheck = messageForCheck;
    checkState(ordinal() == severity);
  }

  @Nullable
  public String getMessageForCheck() {
    return messageForCheck;
  }

  private static final Ordering<ReservationType> ORDERING = new Ordering<ReservationType>() {
    @Override
    public int compare(ReservationType left, ReservationType right) {
      return Integer.compare(left.ordinal(), right.ordinal());
    }
  };

  /**
   * Returns the {@code ReservationType} with the highest severity, used when a label has multiple
   * reservation types and a reservation message is needed.
   *
   * @param types the set of reservation types that a label is associated with.
   * @return the reservation type with the highest severity.
   */
  public static ReservationType getTypeOfHighestSeverity(Set<ReservationType> types) {
    checkArgumentNotNull(types, "types must not be null");
    checkArgument(!types.isEmpty(), "types must not be empty");
    return ORDERING.max(types);
  }
}
