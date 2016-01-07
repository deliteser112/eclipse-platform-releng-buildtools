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

package google.registry.flows;

import static google.registry.testing.DatastoreHelper.createTld;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import google.registry.model.registry.Registry.TldState;
import google.registry.testing.AppEngineRule;
import google.registry.util.DateTimeUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for domain application lifecycle. */
@RunWith(JUnit4.class)
public class EppLifecycleDomainApplicationTest extends EppTestCase {

  private static final DateTime START_OF_GA = DateTime.parse("2014-03-01T00:00:00Z");

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Before
  public void initTld() {
    createTld("example", ImmutableSortedMap.of(
        DateTimeUtils.START_OF_TIME, TldState.SUNRISE,
        START_OF_GA, TldState.GENERAL_AVAILABILITY));
  }

  /** Create the two administrative contacts and two hosts. */
  void createContactsAndHosts() throws Exception {
    DateTime startTime = DateTime.parse("2000-06-01T00:00:00Z");
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        startTime);
    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        "contact_create_response_jd1234.xml",
        startTime.plusMinutes(1));
    assertCommandAndResponse(
        "host_create.xml",
        "host_create_response.xml",
        startTime.plusMinutes(2));
    assertCommandAndResponse(
        "host_create2.xml",
        "host_create2_response.xml",
        startTime.plusMinutes(3));
  }

  @Test
  public void testApplicationDuringSunrise_doesntCreateDomainWithoutAllocation() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createContactsAndHosts();
    // Note that the trademark is valid from 2013-08-09 to 2017-07-23, hence the creation in 2014.
    assertCommandAndResponse(
        "domain_create_sunrise_encoded_mark.xml",
        "domain_create_sunrise_encoded_signed_mark_response.xml",
        DateTime.parse("2014-01-01T00:00:00Z"));
    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        "domain_info_response_testvalidate_doesnt_exist.xml",
        DateTime.parse("2014-01-01T00:01:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainAllocation_succeedsOnlyAsSuperuser() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createContactsAndHosts();
    assertCommandAndResponse(
        "domain_create_sunrise_encoded_mark.xml",
        "domain_create_sunrise_encoded_signed_mark_response.xml",
        DateTime.parse("2014-01-01T00:00:00Z"));
    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        "domain_info_response_testvalidate_doesnt_exist.xml",
        DateTime.parse("2014-01-01T00:01:00Z"));
    assertCommandAndResponse(
        "domain_allocate_testvalidate.xml",
        "domain_allocate_response_testvalidate_only_superuser.xml",
        START_OF_GA.plusDays(1));
    setIsSuperuser(true);
    assertCommandAndResponse(
        "domain_allocate_testvalidate.xml",
        "domain_allocate_response_testvalidate.xml",
        START_OF_GA.plusDays(1).plusMinutes(1));
    setIsSuperuser(false);
    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        "domain_info_response_testvalidate_ok.xml",
        START_OF_GA.plusDays(1).plusMinutes(2));
  }
}
