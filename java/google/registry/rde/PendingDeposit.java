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

package google.registry.rde;

import com.google.auto.value.AutoValue;
import google.registry.model.common.Cursor.CursorType;
import google.registry.model.rde.RdeMode;
import java.io.Serializable;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Container representing a single RDE or BRDA XML escrow deposit that needs to be created. */
@AutoValue
public abstract class PendingDeposit implements Serializable {

  private static final long serialVersionUID = 3141095605225904433L;

  public abstract String tld();
  public abstract DateTime watermark();
  public abstract RdeMode mode();
  public abstract CursorType cursor();
  public abstract Duration interval();

  static PendingDeposit create(
      String tld, DateTime watermark, RdeMode mode, CursorType cursor, Duration interval) {
    return new AutoValue_PendingDeposit(tld, watermark, mode, cursor, interval);
  }

  PendingDeposit() {}
}
