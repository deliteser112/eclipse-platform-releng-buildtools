// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package google.registry.batch;

import com.google.common.base.Optional;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/**
 * Test harness for {@link MapreduceEntityCleanupUtil}.
 *
 * <p>It's a somewhat like a mock, in that it records the number of calls to {@link
 * #findEligibleJobsByJobName}, and it allows the overriding of the number of jobs returned per
 * search, to allow testing of subsequent searches. But it's not a full mock by any means.
 */
public class TestMapreduceEntityCleanupUtil extends MapreduceEntityCleanupUtil {

  private int maxNumberOfJobsPerSearch;
  private int numSearches = 0;

  public TestMapreduceEntityCleanupUtil() {
    this.maxNumberOfJobsPerSearch = MAX_NUMBER_OF_JOBS_PER_SEARCH;
  }

  @Override
  protected int getMaxNumberOfJobsPerSearch() {
    return maxNumberOfJobsPerSearch;
  }

  public void setMaxNumberOfJobsPerSearch(int maxNumberOfJobsPerSearch) {
    this.maxNumberOfJobsPerSearch = maxNumberOfJobsPerSearch;
  }

  @Override
  public EligibleJobResults findEligibleJobsByJobName(
      @Nullable String jobName,
      DateTime cutoffDate,
      Optional<Integer> maxJobs,
      boolean ignoreState,
      Optional<String> cursor) {
    numSearches++;
    return super.findEligibleJobsByJobName(jobName, cutoffDate, maxJobs, ignoreState, cursor);
  }

  public int getNumSearchesPerformed() {
    return numSearches;
  }
}
