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

package google.registry.tools.server;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.persistActiveContact;

import com.google.common.base.Optional;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.contact.ContactResource;
import google.registry.testing.FakeResponse;
import google.registry.testing.mapreduce.MapreduceTestCase;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ResaveAllEppResourcesAction}. */
@RunWith(JUnit4.class)
public class ResaveAllEppResourcesActionTest
    extends MapreduceTestCase<ResaveAllEppResourcesAction> {

  @Before
  public void init() {
    action = new ResaveAllEppResourcesAction();
    action.mrRunner = new MapreduceRunner(Optional.<Integer>of(5), Optional.<Integer>absent());
    action.response = new FakeResponse();
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void test_mapreduceSuccessfullyResavesEntity() throws Exception {
    ContactResource contact = persistActiveContact("test123");
    DateTime creationTime = contact.getUpdateAutoTimestamp().getTimestamp();
    assertThat(ofy().load().entity(contact).now().getUpdateAutoTimestamp().getTimestamp())
        .isEqualTo(creationTime);
    ofy().clearSessionCache();
    runMapreduce();
    assertThat(ofy().load().entity(contact).now().getUpdateAutoTimestamp().getTimestamp())
        .isGreaterThan(creationTime);
  }
}
