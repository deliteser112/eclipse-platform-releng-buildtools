// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

  @Test
  public void testApplicationDuringSunrise_doesntCreateDomainWithoutAllocation() throws Exception {
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    createContactsAndHosts();
    // Note that the trademark is valid from 2013-08-09 to 2017-07-23, hence the creation in 2014.
    assertThatCommand("domain_create_sunrise_encoded_mark.xml")
        .atTime("2014-01-01T00:00:00Z")
        .hasResponse("domain_create_sunrise_encoded_signed_mark_response.xml");
    assertThatCommand("domain_info_testvalidate.xml")
        .atTime("2014-01-01T00:01:00Z")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of(
                "CODE", "2303",
                "MSG", "The domain with given ID (test-validate.example) doesn't exist."));
    assertThatLogoutSucceeds();
  }

  @Test
  public void testDomainAllocation_succeedsOnlyAsSuperuser() throws Exception {
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    createContactsAndHosts();
    assertThatCommand("domain_create_sunrise_encoded_mark.xml")
        .atTime("2014-01-01T00:00:00Z")
        .hasResponse("domain_create_sunrise_encoded_signed_mark_response.xml");
    assertThatCommand("domain_info_testvalidate.xml")
        .atTime("2014-01-01T00:01:00Z")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of(
                "CODE", "2303",
                "MSG", "The domain with given ID (test-validate.example) doesn't exist."));
    assertThatCommand("domain_allocate_testvalidate.xml")
        .atTime(START_OF_GA.plusDays(1))
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of("CODE", "2201", "MSG", "Only a superuser can allocate domains"));
    setIsSuperuser(true);
    assertThatCommand("domain_allocate_testvalidate.xml")
        .atTime(START_OF_GA.plusDays(1).plusMinutes(1))
        .hasResponse("domain_allocate_response_testvalidate.xml");
    setIsSuperuser(false);
    assertThatCommand("domain_info_testvalidate.xml")
        .atTime(START_OF_GA.plusDays(1).plusMinutes(2))
        .hasResponse("domain_info_response_testvalidate_ok.xml");
  }
}
