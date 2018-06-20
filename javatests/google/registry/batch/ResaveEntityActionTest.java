// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.batch;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistDomainWithDependentResources;
import static google.registry.testing.DatastoreHelper.persistDomainWithPendingTransfer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.googlecode.objectify.Key;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.DomainResource;
import google.registry.request.Response;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.ShardableTestCase;
import google.registry.util.Clock;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ResaveEntityAction}. */
@RunWith(JUnit4.class)
public class ResaveEntityActionTest extends ShardableTestCase {

  @Rule
  public final AppEngineRule appEngine =
      AppEngineRule.builder().withDatastore().withTaskQueue().build();

  private final Clock clock = new FakeClock(DateTime.parse("2016-02-11T10:00:00Z"));
  private final Response response = mock(Response.class);

  @Before
  public void before() {
    createTld("tld");
  }

  private void runAction(Key<ImmutableObject> resourceKey, DateTime requestedTime) {
    ResaveEntityAction action = new ResaveEntityAction(resourceKey, requestedTime, clock, response);
    action.run();
  }

  @Test
  public void test_domainPendingTransfer_isResavedAndTransferCompleted() {
    DomainResource domain =
        persistDomainWithPendingTransfer(
            persistDomainWithDependentResources(
                "domain",
                "tld",
                persistActiveContact("jd1234"),
                DateTime.parse("2016-02-06T10:00:00Z"),
                DateTime.parse("2016-02-06T10:00:00Z"),
                DateTime.parse("2017-01-02T10:11:00Z")),
            DateTime.parse("2016-02-06T10:00:00Z"),
            DateTime.parse("2016-02-11T10:00:00Z"),
            DateTime.parse("2017-01-02T10:11:00Z"),
            DateTime.parse("2016-02-06T10:00:00Z"));
    assertThat(domain.getCurrentSponsorClientId()).isEqualTo("TheRegistrar");
    runAction(Key.create(domain), DateTime.parse("2016-02-06T10:00:01Z"));
    DomainResource resavedDomain = ofy().load().entity(domain).now();
    assertThat(resavedDomain.getCurrentSponsorClientId()).isEqualTo("NewRegistrar");
    verify(response).setPayload("Entity re-saved.");
  }
}
