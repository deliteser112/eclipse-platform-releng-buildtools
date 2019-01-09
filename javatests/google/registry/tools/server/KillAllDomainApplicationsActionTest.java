// Copyright 2019 The Nomulus Authors. All Rights Reserved.
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
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.googlecode.objectify.Key;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.index.DomainApplicationIndex;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.FakeResponse;
import google.registry.testing.mapreduce.MapreduceTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link KillAllDomainApplicationsAction}. */
@RunWith(JUnit4.class)
public class KillAllDomainApplicationsActionTest
    extends MapreduceTestCase<KillAllDomainApplicationsAction> {

  private void runMapreduce() throws Exception {
    action = new KillAllDomainApplicationsAction();
    action.mrRunner = makeDefaultRunner();
    action.response = new FakeResponse();
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void test_deletesOnlyApplicationsAndAssociatedEntities() throws Exception {
    createTlds("tld1", "tld2");

    DomainResource domain = persistActiveDomain("foo1.tld1");
    EppResourceIndex domainEri =
        ofy().load().entity(EppResourceIndex.create(Key.create(domain))).now();
    ForeignKeyIndex<DomainResource> domainFki =
        ofy().load().key(ForeignKeyIndex.createKey(domain)).now();
    HistoryEntry domainHistoryEntry =
        persistResource(new HistoryEntry.Builder().setParent(domain).build());

    DomainApplication application = persistActiveDomainApplication("foo2.tld1");
    EppResourceIndex applicationEri =
        ofy().load().entity(EppResourceIndex.create(Key.create(application))).now();
    DomainApplicationIndex applicationDai =
        ofy().load().key(DomainApplicationIndex.createKey(application)).now();
    HistoryEntry applicationHistoryEntry =
        persistResource(new HistoryEntry.Builder().setParent(application).build());

    ContactResource contact = persistActiveContact("foo");
    HostResource host = persistActiveHost("ns.foo.tld1");

    runMapreduce();
    ofy().clearSessionCache();

    // Check that none of the domain, contact, and host entities were deleted.
    assertThat(
            ofy()
                .load()
                .entities(domain, domainEri, domainFki, domainHistoryEntry, contact, host)
                .values())
        .containsExactly(domain, domainEri, domainFki, domainHistoryEntry, contact, host);
    // Check that all of the domain application entities were deleted.
    assertThat(
            ofy()
                .load()
                .entities(application, applicationEri, applicationDai, applicationHistoryEntry))
        .isEmpty();
  }
}
