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

package google.registry.tmch;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistDomainAndEnqueueLordn;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.taskqueue.LeaseOptions;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.apphosting.api.DeadlineExceededException;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.Registrar.Type;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.Clock;
import java.util.List;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link LordnTask}. */
@RunWith(MockitoJUnitRunner.class)
public class LordnTaskTest {

  private static final Clock clock = new FakeClock(DateTime.parse("2010-05-01T10:11:12Z"));

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Before
  public void before() throws Exception {
    createTld("example");
    inject.setStaticField(Ofy.class, "clock", clock);
    inject.setStaticField(LordnTask.class, "backOffMillis", 1L);
  }

  @Test
  public void test_convertTasksToCsv() throws Exception {
    List<TaskHandle> tasks = ImmutableList.of(
        makeTaskHandle("task1", "example", "csvLine1", "lordn-sunrise"),
        makeTaskHandle("task2", "example", "csvLine2", "lordn-sunrise"),
        makeTaskHandle("task3", "example", "ending", "lordn-sunrise"));
    assertThat(LordnTask.convertTasksToCsv(tasks, clock.nowUtc(), "col1,col2"))
        .isEqualTo("1,2010-05-01T10:11:12.000Z,3\ncol1,col2\ncsvLine1\ncsvLine2\nending\n");
  }

  @Test
  public void test_convertTasksToCsv_doesntFailOnEmptyTasks() throws Exception {
    assertThat(
        LordnTask.convertTasksToCsv(ImmutableList.<TaskHandle> of(), clock.nowUtc(), "col1,col2"))
            .isEqualTo("1,2010-05-01T10:11:12.000Z,0\ncol1,col2\n");
  }

  @Test
  public void test_convertTasksToCsv_throwsNpeOnNullTasks() throws Exception {
    thrown.expect(NullPointerException.class);
    LordnTask.convertTasksToCsv(null, clock.nowUtc(), "header");
  }

  private DomainResource.Builder newDomainBuilder(DateTime applicationTime) {
    return new DomainResource.Builder()
        .setFullyQualifiedDomainName("fleece.example")
        .setRegistrant(Key.create(persistActiveContact("jd1234")))
        .setSmdId("smdzzzz")
        .setCreationClientId("TheRegistrar")
        .setApplicationTime(applicationTime);
  }

  @Test
  public void test_enqueueDomainResourceTask_sunrise() throws Exception {
    DomainResource domain = newDomainBuilder(DateTime.parse("2010-05-01T10:11:12Z"))
        .setRepoId("A-EXAMPLE")
        .build();
    persistDomainAndEnqueueLordn(domain);
    String expectedPayload =
        "A-EXAMPLE,fleece.example,smdzzzz,1,2010-05-01T10:11:12.000Z,2010-05-01T10:11:12.000Z";
    assertTasksEnqueued(
        "lordn-sunrise", new TaskMatcher().payload(expectedPayload).tag("example"));
  }

  @Test
  public void test_enqueueDomainResourceTask_claims() throws Exception {
    DateTime time = DateTime.parse("2010-05-01T10:11:12Z");
    DomainResource domain = newDomainBuilder(time)
        .setRepoId("11-EXAMPLE")
        .setLaunchNotice(LaunchNotice.create("landrush1tcn", null, null, time.minusHours(1)))
        .build();
    persistDomainAndEnqueueLordn(domain);
    String expectedPayload = "11-EXAMPLE,fleece.example,landrush1tcn,1,2010-05-01T10:11:12.000Z,"
        + "2010-05-01T09:11:12.000Z,2010-05-01T10:11:12.000Z";
    assertTasksEnqueued(
        "lordn-claims", new TaskMatcher().payload(expectedPayload).tag("example"));
  }

  @Test
  public void test_oteRegistrarWithNullIanaId() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(Registrar.loadByClientId("TheRegistrar").asBuilder()
            .setType(Type.OTE)
            .setIanaIdentifier(null)
            .build());
      }});
    DomainResource domain = newDomainBuilder(DateTime.parse("2010-05-01T10:11:12Z"))
        .setRepoId("3-EXAMPLE")
        .build();
    persistDomainAndEnqueueLordn(domain);
    String expectedPayload =
        "3-EXAMPLE,fleece.example,smdzzzz,null,2010-05-01T10:11:12.000Z,2010-05-01T10:11:12.000Z";
    assertTasksEnqueued(
        "lordn-sunrise", new TaskMatcher().payload(expectedPayload).tag("example"));
  }

  @Test
  public void test_enqueueDomainResourceTask_throwsExceptionOnInvalidRegistrar() throws Exception {
    DateTime time = DateTime.parse("2010-05-01T10:11:12Z");
    DomainResource domain = newDomainBuilder(time)
        .setRepoId("9000-EXAMPLE")
        .setCreationClientId("nonexistentRegistrar")
        .build();
    thrown.expect(NullPointerException.class,
        "No registrar found for client id: nonexistentRegistrar");
    persistDomainAndEnqueueLordn(domain);
  }

  @Test
  public void test_enqueueDomainResourceTask_throwsNpeOnNullDomain() throws Exception {
    thrown.expect(NullPointerException.class);
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        LordnTask.enqueueDomainResourceTask(null);
      }});
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_loadAllTasks_retryLogic_thirdTrysTheCharm() throws Exception {
    Queue queue = mock(Queue.class);
    TaskHandle task = new TaskHandle(TaskOptions.Builder.withTaskName("blah"), "blah");
    when(queue.leaseTasks(any(LeaseOptions.class)))
        .thenThrow(TransientFailureException.class)
        .thenThrow(DeadlineExceededException.class)
        .thenReturn(ImmutableList.<TaskHandle>of(task), ImmutableList.<TaskHandle>of());
    assertThat(LordnTask.loadAllTasks(queue, "tld")).containsExactly(task);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_loadAllTasks_retryLogic_allFailures() throws Exception {
    Queue queue = mock(Queue.class);
    when(queue.leaseTasks(any(LeaseOptions.class))).thenThrow(TransientFailureException.class);
    thrown.expect(RuntimeException.class, "Error leasing tasks");
    LordnTask.loadAllTasks(queue, "tld");
  }

  private static TaskHandle makeTaskHandle(
      String taskName,
      String tag,
      String payload,
      String queue) {
    return new TaskHandle(
        TaskOptions.Builder.withPayload(payload).method(Method.PULL).tag(tag).taskName(taskName),
        queue);
  }
}
