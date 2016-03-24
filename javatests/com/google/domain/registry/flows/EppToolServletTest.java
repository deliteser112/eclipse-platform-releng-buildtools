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

package com.google.domain.registry.flows;

import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.UserInfo;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link EppToolServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class EppToolServletTest extends EppServletTestCase<EppToolServlet> {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .withUserService(UserInfo.createAdmin(GAE_USER_EMAIL, GAE_USER_ID))
      .build();

  private static final String GAE_USER_ID = "12345";
  private static final String GAE_USER_EMAIL = "person@example.com";

  @Before
  public void initTest() throws Exception {
    Registrar registrar = Registrar.loadByClientId("NewRegistrar");
    persistResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setEmailAddress(GAE_USER_EMAIL)
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
            .setGaeUserId(GAE_USER_ID)
            .build());
  }

  @Test
  public void testDomainAllocation_succeedsOnlyAsSuperuser() throws Exception {
    ImmutableSortedMap<DateTime, TldState> transitions = ImmutableSortedMap.of(
        START_OF_TIME, TldState.SUNRISE,
        START_OF_GA, TldState.GENERAL_AVAILABILITY);
    createTld("example", transitions);

    setClientIdentifier("NewRegistrar");
    setSuperuser(false);
    createContactsAndHosts();
    // Note that the trademark is valid from 20130809 to 20170723, hence the domain creation
    // in 2014.
    assertCommandAndResponse(
        "domain_create_sunrise_encoded_mark.xml",
        "domain_create_sunrise_encoded_signed_mark_response.xml",
        "2014-01-01T00:00:00Z");
    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        "domain_info_response_testvalidate_doesnt_exist.xml",
        "2014-01-01T00:01:00Z");
    assertCommandAndResponse(
        "domain_allocate_testvalidate.xml",
        "domain_allocate_response_testvalidate_only_superuser.xml",
        START_OF_GA.plusDays(1));
    setSuperuser(true);
    assertCommandAndResponse(
        "domain_allocate_testvalidate.xml",
        "domain_allocate_response_testvalidate.xml",
        START_OF_GA.plusDays(1).plusMinutes(1));
    setSuperuser(false);
    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        "domain_info_response_testvalidate_ok.xml",
        START_OF_GA.plusDays(1).plusMinutes(2));
  }

  @Test
  public void testDomainCreation_failsBeforeSunrise() throws Exception {
    DateTime sunriseDate = DateTime.parse("2000-05-30T00:00:00Z");
    ImmutableSortedMap<DateTime, TldState> transitions = ImmutableSortedMap.of(
        START_OF_TIME, TldState.PREDELEGATION,
        sunriseDate, TldState.SUNRISE,
        sunriseDate.plusMonths(2), TldState.GENERAL_AVAILABILITY);
    createTld("example", transitions);

    setClientIdentifier("NewRegistrar");
    createContactsAndHosts();
    assertCommandAndResponse(
        "domain_create_sunrise_encoded_mark.xml",
        "domain_create_testvalidate_invalid_phase.xml",
        sunriseDate.minusDays(1));
    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        "domain_info_response_testvalidate_doesnt_exist.xml",
        sunriseDate.plusDays(1));
  }

  @Test
  public void testDomainCheckFee_succeeds() throws Exception {
    DateTime gaDate = DateTime.parse("2000-05-30T00:00:00Z");
    ImmutableSortedMap<DateTime, TldState> transitions = ImmutableSortedMap.of(
        START_OF_TIME, TldState.PREDELEGATION,
        gaDate, TldState.GENERAL_AVAILABILITY);
    createTld("example", transitions);

    setClientIdentifier("NewRegistrar");
    assertCommandAndResponse(
        "domain_check_fee_premium.xml",
        "domain_check_fee_premium_response.xml",
        gaDate.plusDays(1));
  }

  // Extra method so the test runner doesn't produce empty shards.
  @Test public void testNothing1() {}
}
