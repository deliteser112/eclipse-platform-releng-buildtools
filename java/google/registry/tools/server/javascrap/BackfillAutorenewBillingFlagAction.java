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

import static google.registry.mapreduce.MapreduceRunner.PARAM_DRY_RUN;
import static google.registry.mapreduce.inputs.EppResourceInputs.createChildEntityInput;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.VoidWork;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.Recurring;
import google.registry.model.domain.DomainResource;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;

/** A mapreduce that backfills new {@link Flag#AUTO_RENEW} flag on recurring billing events. */
@Action(path = "/_dr/task/backfillAutorenewBillingFlag")
public class BackfillAutorenewBillingFlagAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject MapreduceRunner mrRunner;
  @Inject @Parameter(PARAM_DRY_RUN) boolean isDryRun;
  @Inject Response response;
  @Inject BackfillAutorenewBillingFlagAction() {}

  @Override
  public void run() {
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Backfill AUTO_RENEW flag on Recurring billing events")
        .setModuleName("tools")
        .runMapOnly(
            new BackfillAutorenewBillingFlagMapper(isDryRun),
            ImmutableList.of(createRecurringInput()))));
  }

  private Input<? extends Recurring> createRecurringInput() {
    return createChildEntityInput(
        ImmutableSet.<Class<? extends DomainResource>>of(DomainResource.class),
        ImmutableSet.<Class<? extends Recurring>>of(Recurring.class));
  }

  /** Mapper to count BillingEvent.Recurring resources. */
  public static class BackfillAutorenewBillingFlagMapper extends Mapper<Recurring, Void, Void> {

    private static final long serialVersionUID = -6576637759905280988L;

    private final boolean isDryRun;

    public BackfillAutorenewBillingFlagMapper(boolean isDryRun) {
      this.isDryRun = isDryRun;
    }

    @Override
    public final void map(final Recurring recurring) {
      try {
        // A note on how this works: Since OnLoad makes the backfill change for us, all we need to
        // do is check that this condition exists on the loaded entity, and just re-save to persist
        // the new data to datastore.
        if (!isDryRun) {
          ofy().transactNew(new VoidWork() {
            @Override
            public void vrun() {
              ofy().save().entity(ofy().load().entity(recurring).now());
            }
          });
          getContext().incrementCounter("Saved Recurring billing events");
        }
        getContext().incrementCounter("Recurring billing events encountered");
      } catch (Throwable t) {
        logger.severe(t, "Error while backfilling AUTO_RENEW flags.");
        getContext().incrementCounter("error: " + t.getClass().getSimpleName());
      }
    }
  }
}
