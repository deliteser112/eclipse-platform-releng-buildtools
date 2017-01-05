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

package google.registry.util;

import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static google.registry.config.RegistryConfig.getSingletonCachePersistDuration;
import static google.registry.config.RegistryConfig.getSingletonCacheRefreshDuration;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.joda.time.Duration.ZERO;

import com.google.common.base.Supplier;
import org.joda.time.Duration;

/** Utility methods related to caching. */
public class CacheUtils {

  /**
   * Memoize a supplier, with a short expiration specified in the environment config.
   *
   * <p>Use this for things that might change while code is running. (For example, the various
   * lists downloaded from the TMCH get updated in datastore and the caches need to be refreshed.)
   */
  public static <T> Supplier<T> memoizeWithShortExpiration(Supplier<T> original) {
    return memoizeForDuration(original, getSingletonCacheRefreshDuration());
  }

  /**
   * Memoize a supplier, with a long expiration specified in the environment config.
   *
   * <p>Use this for things that are loaded lazily but then will never change. This allows the test
   * config to set the expiration time to zero so that different test values can be substituted in,
   * while allowing the production config to set the expiration to forever.
   */
  public static <T> Supplier<T> memoizeWithLongExpiration(Supplier<T> original) {
    return memoizeForDuration(original, getSingletonCachePersistDuration());
  }

  /** Memoize a supplier, with a given expiration. */
  private static <T> Supplier<T> memoizeForDuration(Supplier<T> original, Duration expiration) {
    return expiration.isEqual(ZERO)
        ? original  // memoizeWithExpiration won't accept 0 as a refresh duration.
        : memoizeWithExpiration(original, expiration.getMillis(), MILLISECONDS);
  }
}

