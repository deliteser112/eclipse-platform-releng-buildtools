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

import com.google.auto.value.AutoValue;
import google.registry.proxy.quota.QuotaManager.QuotaResponse.Status;
import google.registry.proxy.quota.TokenStore.TimestampedInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.concurrent.ThreadSafe;
import org.joda.time.DateTime;

/**
 * A thread-safe quota manager that schedules background refresh if necessary.
 *
 * <p>This class abstracts away details about the {@link TokenStore}. It:
 *
 * <ul>
 *   <li>Translates a {@link QuotaRequest} to taking one token from the store, blocks the caller,
 *       and responds with a {@link QuotaResponse}.
 *   <li>Translates a {@link QuotaRebate} to putting the token to the store asynchronously, and
 *       immediately returns.
 *   <li>Periodically refreshes the token records asynchronously to purge stale recodes.
 * </ul>
 *
 * <p>There should be one {@link QuotaManager} per protocol.
 */
@ThreadSafe
public class QuotaManager {

  @AutoValue
  abstract static class QuotaRequest {

    static QuotaRequest create(String userId) {
      return new AutoValue_QuotaManager_QuotaRequest(userId);
    }

    abstract String userId();
  }

  @AutoValue
  abstract static class QuotaResponse {

    enum Status {
      SUCCESS,
      FAILURE,
    }

    static QuotaResponse create(Status status, String userId, DateTime grantedTokenRefillTime) {
      return new AutoValue_QuotaManager_QuotaResponse(status, userId, grantedTokenRefillTime);
    }

    abstract Status status();

    abstract String userId();

    abstract DateTime grantedTokenRefillTime();
  }

  @AutoValue
  abstract static class QuotaRebate {
    static QuotaRebate create(QuotaResponse response) {
      return new AutoValue_QuotaManager_QuotaRebate(
          response.userId(), response.grantedTokenRefillTime());
    }

    abstract String userId();

    abstract DateTime grantedTokenRefillTime();
  }

  private final TokenStore tokenStore;

  private final ExecutorService backgroundExecutor;

  QuotaManager(TokenStore tokenStore, ExecutorService backgroundExecutor) {
    this.tokenStore = tokenStore;
    this.backgroundExecutor = backgroundExecutor;
    tokenStore.scheduleRefresh();
  }

  /** Attempts to acquire requested quota, synchronously. */
  QuotaResponse acquireQuota(QuotaRequest request) {
    TimestampedInteger tokens = tokenStore.take(request.userId());
    Status status = (tokens.value() == 0) ? Status.FAILURE : Status.SUCCESS;
    return QuotaResponse.create(status, request.userId(), tokens.timestamp());
  }

  /**
   * Returns granted quota to the token store, asynchronously.
   *
   * @return a {@link Future} representing the asynchronous task to return the quota.
   */
  Future<?> releaseQuota(QuotaRebate rebate) {
    return backgroundExecutor.submit(
        () -> tokenStore.put(rebate.userId(), rebate.grantedTokenRefillTime()));
  }
}
