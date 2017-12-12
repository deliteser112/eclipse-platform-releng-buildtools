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

package google.registry.proxy.quota;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import google.registry.proxy.ProxyConfig.Quota;
import google.registry.proxy.ProxyConfig.Quota.QuotaGroup;
import org.joda.time.Duration;

/** Value class that stores the quota configuration for a protocol. */
public class QuotaConfig {

  private final int refreshSeconds;
  private final QuotaGroup defaultQuota;
  private final ImmutableMap<String, QuotaGroup> customQuotaMap;

  /**
   * Constructs a {@link QuotaConfig} from a {@link Quota}.
   *
   * <p>Each {@link QuotaGroup} is keyed to all the {@code userId}s it contains. This allows for
   * fast lookup with a {@code userId}.
   */
  public QuotaConfig(Quota quota) {
    refreshSeconds = quota.refreshSeconds;
    defaultQuota = quota.defaultQuota;
    ImmutableMap.Builder<String, QuotaGroup> mapBuilder = new ImmutableMap.Builder<>();
    quota.customQuota.forEach(
        quotaGroup -> quotaGroup.userId.forEach(userId -> mapBuilder.put(userId, quotaGroup)));
    customQuotaMap = mapBuilder.build();
  }

  @VisibleForTesting
  QuotaGroup findQuotaGroup(String userId) {
    return customQuotaMap.getOrDefault(userId, defaultQuota);
  }

  /** Returns the token amount for the given {@code userId}. */
  public int getTokenAmount(String userId) {
    return findQuotaGroup(userId).tokenAmount;
  }

  /** Returns the refill period for the given {@code userId}. */
  public Duration getRefillPeriod(String userId) {
    return Duration.standardSeconds(findQuotaGroup(userId).refillSeconds);
  }

  /** Returns the refresh period for this quota config. */
  public Duration getRefreshPeriod() {
    return Duration.standardSeconds(refreshSeconds);
  }
}
