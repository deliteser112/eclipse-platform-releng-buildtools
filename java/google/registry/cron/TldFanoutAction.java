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

package google.registry.cron;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Multimaps.filterKeys;
import static com.google.common.collect.Sets.difference;
import static google.registry.model.registry.Registries.getTldsOfType;
import static google.registry.model.registry.Registry.TldType.REAL;
import static google.registry.model.registry.Registry.TldType.TEST;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.ParameterMap;
import google.registry.request.RequestParameters;
import google.registry.util.TaskEnqueuer;
import java.util.Random;
import java.util.Set;
import javax.inject.Inject;

/**
 * Action for fanning out cron tasks shared by TLD.
 *
 * <h3>Parameters Reference</h3>
 *
 * <ul>
 * <li>{@code endpoint} (Required) URL path of servlet to launch. This may contain pathargs.
 * <li>{@code queue} (Required) Name of the App Engine push queue to which this task should be sent.
 * <li>{@code forEachRealTld} Launch the task in each real TLD namespace.
 * <li>{@code forEachTestTld} Launch the task in each test TLD namespace.
 * <li>{@code runInEmpty} Launch the task in the empty namespace.
 * <li>{@code exclude} TLDs to exclude.
 * <li>{@code jitterSeconds} Randomly delay each task by up to this many seconds.
  * <li>Any other parameters specified will be passed through as POST parameters to the called task.
 * </ul>
 *
 * <h3>Patharg Reference</h3>
 *
 * <p>The following values may be specified inside the "endpoint" param.
 * <ul>
 * <li>{@code :tld} Substituted with an ASCII tld, if tld fanout is enabled.
 *   This patharg is mostly useful for aesthetic purposes, since tasks are already namespaced.
 * </ul>
 */
@Action(path = "/_dr/cron/fanout", automaticallyPrintOk = true)
public final class TldFanoutAction implements Runnable {

  private static final String ENDPOINT_PARAM = "endpoint";
  private static final String QUEUE_PARAM = "queue";
  private static final String FOR_EACH_REAL_TLD_PARAM = "forEachRealTld";
  private static final String FOR_EACH_TEST_TLD_PARAM = "forEachTestTld";
  private static final String RUN_IN_EMPTY_PARAM = "runInEmpty";
  private static final String EXCLUDE_PARAM = "exclude";
  private static final String JITTER_SECONDS_PARAM = "jitterSeconds";

  /** A set of control params to TldFanoutAction that aren't passed down to the executing action. */
  private static final Set<String> CONTROL_PARAMS = ImmutableSet.of(
      ENDPOINT_PARAM,
      QUEUE_PARAM,
      FOR_EACH_REAL_TLD_PARAM,
      FOR_EACH_TEST_TLD_PARAM,
      RUN_IN_EMPTY_PARAM,
      EXCLUDE_PARAM,
      JITTER_SECONDS_PARAM);

  private static final String TLD_PATHARG = ":tld";
  private static final Random random = new Random();

  @Inject TaskEnqueuer taskEnqueuer;
  @Inject @Parameter(ENDPOINT_PARAM) String endpoint;
  @Inject @Parameter(QUEUE_PARAM) String queue;
  @Inject @Parameter(FOR_EACH_REAL_TLD_PARAM) boolean forEachRealTld;
  @Inject @Parameter(FOR_EACH_TEST_TLD_PARAM) boolean forEachTestTld;
  @Inject @Parameter(RUN_IN_EMPTY_PARAM) boolean runInEmpty;
  @Inject @Parameter(EXCLUDE_PARAM) ImmutableSet<String> excludes;
  @Inject @Parameter(JITTER_SECONDS_PARAM) Optional<Integer> jitterSeconds;
  @Inject @ParameterMap ImmutableListMultimap<String, String> params;
  @Inject TldFanoutAction() {}

  @Override
  public void run() {
    Set<String> namespaces = ImmutableSet.copyOf(concat(
        runInEmpty ? ImmutableSet.of("") : ImmutableSet.<String>of(),
        forEachRealTld ? getTldsOfType(REAL) : ImmutableSet.<String>of(),
        forEachTestTld ? getTldsOfType(TEST) : ImmutableSet.<String>of()));
    Multimap<String, String> flowThruParams = filterKeys(params, not(in(CONTROL_PARAMS)));
    Queue taskQueue = getQueue(queue);
    for (String namespace : difference(namespaces, excludes)) {
      taskEnqueuer.enqueue(taskQueue, createTaskOptions(namespace, flowThruParams));
    }
  }

  private TaskOptions createTaskOptions(String tld, Multimap<String, String> params) {
    TaskOptions options =
        withUrl(endpoint.replace(TLD_PATHARG, String.valueOf(tld)))
            .countdownMillis(
                jitterSeconds.isPresent()
                    ? random.nextInt((int) SECONDS.toMillis(jitterSeconds.get()))
                    : 0);
    options.param(RequestParameters.PARAM_TLD, tld);
    for (String param : params.keySet()) {
      // TaskOptions.param() does not accept null values.
      options.param(param, nullToEmpty((getFirst(params.get(param), null))));
    }
    return options;
  }
}
