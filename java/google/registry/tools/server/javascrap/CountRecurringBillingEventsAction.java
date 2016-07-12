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

package google.registry.tools.server.javascrap;

import static google.registry.mapreduce.inputs.EppResourceInputs.createChildEntityInput;
import static google.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.EppResource;
import google.registry.model.billing.BillingEvent.Recurring;
import google.registry.request.Action;
import google.registry.request.Response;
import javax.inject.Inject;

/**
 * A mapreduce that counts all BillingEvent.Recurring entities.
 *
 * <p>This is a test of the ChildEntityInput/ChildEntityReader classes.
 */
@Action(path = "/_dr/task/countRecurringBillingEvents")
public class CountRecurringBillingEventsAction implements Runnable {

  // TODO(b/27562876): Delete this mapreduce once tested in production.

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject CountRecurringBillingEventsAction() {}

  @Override
  public void run() {
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Count recurring billing events")
        .setModuleName("tools")
        .runMapOnly(
            new CountRecurringBillingEventsMapper(),
            ImmutableList.of(createRecurringInput()))));
  }

  private Input<? extends Recurring> createRecurringInput() {
    return createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(EppResource.class),
        ImmutableSet.<Class<? extends Recurring>>of(Recurring.class));
  }

  /** Mapper to count BillingEvent.Recurring resources. */
  public static class CountRecurringBillingEventsMapper extends Mapper<Recurring, Void, Void> {

    private static final long serialVersionUID = -8547238315947793512L;

    public CountRecurringBillingEventsMapper() {}

    @Override
    public final void map(final Recurring recurring) {
      getContext().incrementCounter("recurring billing events");
    }
  }
}
