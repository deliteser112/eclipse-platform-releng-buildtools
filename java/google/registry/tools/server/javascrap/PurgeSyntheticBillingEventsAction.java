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

package google.registry.tools.server.javascrap;

import static google.registry.mapreduce.MapreduceRunner.PARAM_DRY_RUN;
import static google.registry.mapreduce.inputs.EppResourceInputs.createChildEntityInput;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.VoidWork;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.NullInput;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.OneTime;
import google.registry.model.domain.DomainResource;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * A mapreduce that purges {@link Flag#SYNTHETIC} {@link OneTime} billing events, in the event
 * the recurring billing event mapreduce goes south.
 */
@Action(path = "/_dr/task/purgeSyntheticBillingEvents")
public class PurgeSyntheticBillingEventsAction implements Runnable {

  // TODO(b/27562876): Delete once ExpandRecurringBillingEventsAction is verified in production.

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject Clock clock;
  @Inject MapreduceRunner mrRunner;
  @Inject @Parameter(PARAM_DRY_RUN) boolean isDryRun;
  @Inject Response response;
  @Inject PurgeSyntheticBillingEventsAction() {}

  @Override
  public void run() {
    DateTime executeTime = clock.nowUtc();
    logger.infofmt("Running synthetic billing event purge at %s.", executeTime);
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Purge synthetic billing events.")
        .setModuleName("tools")
        .runMapOnly(
            new PurgeSyntheticBillingEventsMapper(isDryRun),
            ImmutableList.of(
                new NullInput<OneTime>(),
                createChildEntityInput(
                    ImmutableSet.<Class<? extends DomainResource>>of(DomainResource.class),
                    ImmutableSet.<Class<? extends OneTime>>of(OneTime.class))))));
  }

  /** Mapper to purge {@link Flag#SYNTHETIC} {@link OneTime} billing events. */
  public static class PurgeSyntheticBillingEventsMapper extends Mapper<OneTime, Void, Void> {

    private static final long serialVersionUID = 8376442755556228455L;

    private final boolean isDryRun;
    private final String syntheticCounterName;
    private static final String ONETIME_COUNTER = "OneTime billing events encountered";

    public PurgeSyntheticBillingEventsMapper(boolean isDryRun) {
      this.isDryRun = isDryRun;
      this.syntheticCounterName = isDryRun
          ? "Synthetic OneTime billing events (dry run)"
          : "Synthetic OneTime billing events deleted";
    }

    @Override
    public final void map(final OneTime oneTime) {
      // A null input ensures the mapper gets called at least once, and initialize the counters.
      if (oneTime == null) {
        getContext().getCounter(syntheticCounterName);
        getContext().getCounter(ONETIME_COUNTER);
        return;
      }
      getContext().incrementCounter("OneTime billing events encountered");
      try {
        ofy().transactNew(new VoidWork() {
          @Override
          public void vrun() {
            if (oneTime.getFlags().contains(Flag.SYNTHETIC)) {
              if (!isDryRun) {
                ofy().delete().entity(oneTime).now();
              }
              getContext().incrementCounter(syntheticCounterName);
            }
          }
        });
      } catch (Throwable t) {
        logger.severefmt(
            t, "Error while deleting synthetic OneTime billing event %s", oneTime.getId());
        getContext().incrementCounter("error: " + t.getClass().getSimpleName());
        throw t;
      }
    }
  }
}
