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

package com.google.domain.registry.monitoring.whitebox;

import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.deleteResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.LogsSubject.assertAboutLogs;
import static java.util.logging.Level.SEVERE;

import com.google.common.base.Optional;
import com.google.common.testing.TestLogHandler;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.index.ForeignKeyIndex;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.mapreduce.MapreduceTestCase;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link VerifyEntityIntegrityAction}. */
@RunWith(JUnit4.class)
public class VerifyEntityIntegrityActionTest
    extends MapreduceTestCase<VerifyEntityIntegrityAction> {

  private TestLogHandler handler;

  @Before
  public void before() {
    createTld("tld");

    action = new VerifyEntityIntegrityAction();
    handler = new TestLogHandler();
    VerifyEntityIntegrityAction.logger.addHandler(handler);
    action.mrRunner = new MapreduceRunner(Optional.of(2), Optional.of(2));
    action.response = new FakeResponse();
  }

  @After
  public void after() {
    VerifyEntityIntegrityAction.logger.removeHandler(handler);
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void test_singleDomain_noBadInvariants() throws Exception {
    persistActiveDomain("ninetails.tld");
    runMapreduce();
    assertAboutLogs().that(handler).hasNoLogsAtLevel(SEVERE);
  }

  @Test
  public void test_singleDomain_missingFki() throws Exception {
    persistActiveDomain("ninetails.tld");
    ForeignKeyIndex<DomainResource> fki =
        ForeignKeyIndex.load(DomainResource.class, "ninetails.tld", DateTime.now(DateTimeZone.UTC));
    deleteResource(fki);
    runMapreduce();
    // TODO(mcilwain): Check for exception message here.
    assertAboutLogs()
        .that(handler)
        .hasLogAtLevelWithMessage(
            SEVERE, "Integrity error found while checking foreign key contraints");
  }
}
