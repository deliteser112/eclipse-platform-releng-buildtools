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

package google.registry.model;

import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.base.Optional;
import google.registry.model.translators.UpdateAutoTimestampTranslatorFactory;
import org.joda.time.DateTime;

/**
 * A timestamp that auto-updates on each save to datastore.
 *
 * @see UpdateAutoTimestampTranslatorFactory
 */
public class UpdateAutoTimestamp extends ImmutableObject {

  DateTime timestamp;

  /** Returns the timestamp, or {@link #START_OF_TIME} if it's null. */
  public DateTime getTimestamp() {
    return Optional.fromNullable(timestamp).or(START_OF_TIME);
  }

  public static UpdateAutoTimestamp create(DateTime timestamp) {
    UpdateAutoTimestamp instance = new UpdateAutoTimestamp();
    instance.timestamp = timestamp;
    return instance;
  }
}
