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

package google.registry.tools.server;

import static com.google.appengine.tools.pipeline.PipelineServiceFactory.newPipelineService;
import static google.registry.request.Action.Method.POST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.appengine.tools.mapreduce.MapReduceResult;
import com.google.appengine.tools.pipeline.JobInfo;
import com.google.appengine.tools.pipeline.NoSuchObjectException;
import com.google.common.collect.ImmutableMap;
import google.registry.request.Action;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.JsonResponse;
import google.registry.request.Parameter;
import google.registry.request.auth.Auth;
import javax.inject.Inject;

/** Action to poll the status of a mapreduce job. */
@Action(path = PollMapreduceAction.PATH, method = POST, auth = Auth.AUTH_INTERNAL_ONLY)
public class PollMapreduceAction implements Runnable {

  public static final String PATH = "/_dr/task/pollMapreduce";

  @Inject @Parameter("jobId") String jobId;
  @Inject JsonResponse response;
  @Inject PollMapreduceAction() {}

  @Override
  public void run() {
    JobInfo jobInfo;
    try {
      jobInfo = newPipelineService().getJobInfo(jobId);
    } catch (NoSuchObjectException e) {
      throw new InternalServerErrorException("Job not found: " + e);
    }
    ImmutableMap.Builder<String, String> json = new ImmutableMap.Builder<>();
    json.put("state", jobInfo.getJobState().toString());
    if (jobInfo.getJobState() == JobInfo.State.COMPLETED_SUCCESSFULLY) {
      json.put("output", ((MapReduceResult<?>) jobInfo.getOutput()).getOutputResult().toString());
    }
    response.setPayload(json.build());
    response.setStatus(SC_OK);
  }
}
