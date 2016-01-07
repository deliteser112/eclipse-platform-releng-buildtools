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

package google.registry.cron;

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Lists.transform;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static java.util.Arrays.asList;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldType;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.Retrier;
import google.registry.util.TaskEnqueuer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TldFanoutAction}. */
@RunWith(JUnit4.class)
public class TldFanoutActionTest {

  private static final String ENDPOINT = "/the/servlet";
  private static final String QUEUE = "the-queue";

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue(Joiner.on('\n').join(
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
          "<queue-entries>",
          "  <queue>",
          "    <name>the-queue</name>",
          "    <rate>1/s</rate>",
          "  </queue>",
          "</queue-entries>"))
      .build();

  private static ImmutableListMultimap<String, String> getParamsMap(String... keysAndValues) {
    ImmutableListMultimap.Builder<String, String> params = new ImmutableListMultimap.Builder<>();
    params.put("queue", QUEUE);
    params.put("endpoint", ENDPOINT);
    for (int i = 0; i < keysAndValues.length; i += 2) {
      params.put(keysAndValues[i], keysAndValues[i + 1]);
    }
    return params.build();
  }

  private static void run(ImmutableListMultimap<String, String> params) throws Exception {
    TldFanoutAction action = new TldFanoutAction();
    action.params = params;
    action.endpoint = getLast(params.get("endpoint"));
    action.queue = getLast(params.get("queue"));
    action.excludes = params.containsKey("exclude")
        ? ImmutableSet.copyOf(Splitter.on(',').split(params.get("exclude").get(0)))
        : ImmutableSet.<String>of();
    action.taskEnqueuer = new TaskEnqueuer(new Retrier(null, 1));
    action.runInEmpty = params.containsKey("runInEmpty");
    action.forEachRealTld = params.containsKey("forEachRealTld");
    action.forEachTestTld = params.containsKey("forEachTestTld");
    action.jitterSeconds = Optional.absent();
    action.run();
  }

  @Before
  public void before() throws Exception {
    createTlds("com", "net", "org", "example");
    persistResource(Registry.get("example").asBuilder().setTldType(TldType.TEST).build());
  }

  private static void assertTasks(String... tasks) throws Exception {
    assertTasksEnqueued(
        QUEUE,
        transform(asList(tasks), new Function<String, TaskMatcher>() {
          @Override
          public TaskMatcher apply(String namespace) {
            return new TaskMatcher()
                .url(ENDPOINT)
                .header("content-type", "application/x-www-form-urlencoded")
                .param("tld", namespace);
          }}));
  }

  @Test
  public void testSuccess_pathargTld() throws Exception {
    run(getParamsMap(
        "forEachRealTld", "",
        "endpoint", "/the/servlet/:tld"));
    assertTasksEnqueued(QUEUE,
        new TaskMatcher().url("/the/servlet/com"),
        new TaskMatcher().url("/the/servlet/net"),
        new TaskMatcher().url("/the/servlet/org"));
  }

  @Test
  public void testSuccess_methodPostIsDefault() throws Exception {
    run(getParamsMap("runInEmpty", ""));
    assertTasksEnqueued(QUEUE, new TaskMatcher().method("POST"));
  }

  @Test
  public void testSuccess_noTlds() throws Exception {
    run(getParamsMap());
    assertNoTasksEnqueued(QUEUE);
  }

  @Test
  public void testSuccess_runInEmpty() throws Exception {
    run(getParamsMap("runInEmpty", ""));
    assertTasks("");
  }

  @Test
  public void testSuccess_forEachRealTld() throws Exception {
    run(getParamsMap("forEachRealTld", ""));
    assertTasks("com", "net", "org");
  }

  @Test
  public void testSuccess_forEachTestTld() throws Exception {
    run(getParamsMap("forEachTestTld", ""));
    assertTasks("example");
  }

  @Test
  public void testSuccess_runInEmptyAndRunInRealTld() throws Exception {
    run(getParamsMap("runInEmpty", "", "forEachRealTld", ""));
    assertTasks("", "com", "net", "org");
  }

  @Test
  public void testSuccess_forEachTestTldAndForEachRealTld() throws Exception {
    run(getParamsMap(
        "forEachTestTld", "",
        "forEachRealTld", ""));
    assertTasks("com", "net", "org", "example");
  }

  @Test
  public void testSuccess_runInEmptyAndForEachTestTld() throws Exception {
    run(getParamsMap("runInEmpty", "", "forEachTestTld", ""));
    assertTasks("", "example");
  }

  @Test
  public void testSuccess_runEverywhere() throws Exception {
    run(getParamsMap("runInEmpty", "", "forEachTestTld", "", "forEachRealTld", ""));
    assertTasks("", "com", "net", "org", "example");
  }

  @Test
  public void testSuccess_excludeRealTlds() throws Exception {
    run(getParamsMap(
        "forEachRealTld", "",
        "exclude", "com,net"));
    assertTasks("org");
  }

  @Test
  public void testSuccess_excludeTestTlds() throws Exception {
    run(getParamsMap(
        "forEachTestTld", "",
        "exclude", "example"));
    assertNoTasksEnqueued(QUEUE);
  }

  @Test
  public void testSuccess_excludeNonexistentTlds() throws Exception {
    run(getParamsMap(
        "runInEmpty", "",
        "forEachTestTld", "",
        "forEachRealTld", "",
        "exclude", "foo"));
    assertTasks("", "com", "net", "org", "example");
  }

  @Test
  public void testSuccess_additionalArgsFlowThroughToPostParams() throws Exception {
    run(getParamsMap("forEachTestTld", "", "newkey", "newval"));
    assertTasksEnqueued(QUEUE,
        new TaskMatcher().url("/the/servlet").param("newkey", "newval"));
  }
}
