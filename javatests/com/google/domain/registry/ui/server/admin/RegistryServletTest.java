// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.ui.server.admin;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.registry.Registries.getTlds;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/** Tests for {@link RegistryServlet}. */
@RunWith(JUnit4.class)
public class RegistryServletTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  HttpServletRequest req;

  @Rule
  public final InjectRule inject = new InjectRule();

  FakeClock clock = new FakeClock();

  DateTime now = DateTime.now(UTC);

  DateTime creationTime;

  private static final Map<String, ?> SUCCESS = ImmutableMap.of("results", "OK");

  private Registry createRegistry(String tld, TldState tldState) {
    return createRegistry(tld, ImmutableSortedMap.of(START_OF_TIME, tldState));
  }

  private Registry createRegistry(
      String tld, ImmutableSortedMap<DateTime, TldState> tldStateTransitions) {
    return new Registry.Builder()
        .setTldStr(tld)
        .setTldStateTransitions(tldStateTransitions)
        .build();
  }

  @Before
  public void setUp() {
    inject.setStaticField(Ofy.class, "clock", clock);
    req = Mockito.mock(HttpServletRequest.class);
    Mockito.when(req.getContextPath()).thenReturn("/_dr/admin");
  }

  @Test
  public void testParsePath() {
    Mockito.when(req.getRequestURI()).thenReturn("/_dr/admin/registry/foo");
    assertThat(new RegistryServlet().parsePath(req)).isEqualTo("registry/foo");
  }

  @Test
  public void testParseId() {
    Mockito.when(req.getRequestURI()).thenReturn("/_dr/admin/registry/foo");
    assertThat(new RegistryServlet().parseId(req)).isEqualTo("foo");
  }

  @Test
  public void testPost() {
    String tld = "xn--q9jyb4c";
    Mockito.when(req.getRequestURI()).thenReturn("/_dr/admin/registry/" + tld);

    assertThat(getTlds()).doesNotContain(tld);

    clock.setTo(clock.nowUtc().plusMinutes(1));
    new RegistryServlet().doJsonPost(req, ImmutableMap.of("op", "create"));
    creationTime = clock.nowUtc();

    assertThat(Registry.get(tld)).isEqualTo(
        createRegistry(tld, TldState.PREDELEGATION));

    clock.setTo(clock.nowUtc().plusMinutes(1));
    Map<String, ?> result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "tldStateTransitions", ImmutableList.of(
                ImmutableMap.of(
                    "transitionTime", START_OF_TIME.toString(), "tldState", "PREDELEGATION"),
                ImmutableMap.of(
                    "transitionTime", now.toString(), "tldState", "SUNRUSH")))));
    assertThat(result).isEqualTo(SUCCESS);
    assertThat(Registry.get(tld)).isEqualTo(
        createRegistry(tld,
          ImmutableSortedMap.of(
            START_OF_TIME, TldState.PREDELEGATION,
            now, TldState.SUNRUSH)));

    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of()));
    assertThat(result).isEqualTo(SUCCESS);
    assertThat(Registry.get(tld)).isEqualTo(
        createRegistry(tld,
          ImmutableSortedMap.of(
            START_OF_TIME, TldState.PREDELEGATION,
            now, TldState.SUNRUSH)));

    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "tldStateTransitions", ImmutableList.of(
                ImmutableMap.of(
                    "transitionTime", START_OF_TIME.toString(), "tldState", "PREDELEGATION"),
                ImmutableMap.of(
                    "transitionTime", now.toString(), "tldState", "SUNRISE"),
                ImmutableMap.of(
                    "transitionTime", now.plusMonths(1).toString(), "tldState", "SUNRUSH")))));
    assertThat(result).isEqualTo(SUCCESS);
    assertThat(Registry.get(tld)).isEqualTo(
        createRegistry(tld,
            ImmutableSortedMap.of(
                START_OF_TIME, TldState.PREDELEGATION,
                now, TldState.SUNRISE,
                now.plusMonths(1), TldState.SUNRUSH)));

    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "tldStateTransitions", ImmutableList.of(
                ImmutableMap.of(
                    "transitionTime", START_OF_TIME.toString(), "tldState", "PREDELEGATION"),
                ImmutableMap.of(
                    "transitionTime", now.toString(), "tldState", "SUNRISE"),
                ImmutableMap.of(
                    "transitionTime", now.plusMonths(1).toString(), "tldState", "SUNRUSH"),
                ImmutableMap.of(
                    "transitionTime", now.plusMonths(2).toString(), "tldState", "QUIET_PERIOD"),
                ImmutableMap.of(
                    "transitionTime", now.plusMonths(3).toString(),
                    "tldState", "GENERAL_AVAILABILITY")))));
    assertThat(result).isEqualTo(SUCCESS);
    assertThat(Registry.get(tld)).isEqualTo(
        createRegistry(tld,
          ImmutableSortedMap.of(
            START_OF_TIME, TldState.PREDELEGATION,
            now, TldState.SUNRISE,
            now.plusMonths(1), TldState.SUNRUSH,
            now.plusMonths(2), TldState.QUIET_PERIOD,
            now.plusMonths(3), TldState.GENERAL_AVAILABILITY)));

    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "tldStateTransitions", ImmutableList.of(
                ImmutableMap.of(
                    "transitionTime", START_OF_TIME.toString(), "tldState", "PREDELEGATION"),
                ImmutableMap.of(
                    "transitionTime", now.toString(), "tldState", "SUNRUSH"),
                ImmutableMap.of(
                    "transitionTime", now.plusMonths(1).toString(),
                    "tldState", "GENERAL_AVAILABILITY")))));
    assertThat(result).isEqualTo(SUCCESS);
    assertThat(Registry.get(tld)).isEqualTo(
        createRegistry(tld,
          ImmutableSortedMap.of(
            START_OF_TIME, TldState.PREDELEGATION,
            now, TldState.SUNRUSH,
            now.plusMonths(1), TldState.GENERAL_AVAILABILITY)));

    Registry tldRegistry = Registry.get(tld);

    // Switch to foobar.
    Mockito.when(req.getRequestURI()).thenReturn("/_dr/admin/registry/foobar");

    clock.setTo(clock.nowUtc().plusMinutes(1));
    new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "create"));
    creationTime = clock.nowUtc();

    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "tldStateTransitions", ImmutableList.of(
                ImmutableMap.of(
                    "transitionTime", START_OF_TIME.toString(), "tldState", "QUIET_PERIOD")))));
    assertThat(result).isEqualTo(SUCCESS);
    assertThat(Registry.get("foobar")).isEqualTo(
        createRegistry("foobar", ImmutableSortedMap.of(START_OF_TIME, TldState.QUIET_PERIOD)));

    // Make sure updating this new TLD "foobar" didn't modify the tld we are using.
    assertThat(Registry.get(tld)).isEqualTo(tldRegistry);

    // This should fail since the states are out of order (SUNRISE comes before SUNRUSH).
    Mockito.when(req.getRequestURI()).thenReturn("/_dr/admin/registry/" + tld);
    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "id", tld,
            "tldStateTransitions", ImmutableList.of(
                ImmutableMap.of(
                    "transitionTime", START_OF_TIME.toString(), "tldState", "PREDELEGATION"),
                ImmutableMap.of(
                    "transitionTime", now.toString(), "tldState", "SUNRUSH"),
                ImmutableMap.of(
                    "transitionTime", now.plusMonths(1).toString(), "tldState", "SUNRISE")))));
    assertThat(result).isNotEqualTo(SUCCESS);

    Mockito.when(req.getRequestURI()).thenReturn("/_dr/admin/registry/xn--q9jyb4c");

    // This should fail since the same state appears twice.
    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "tldStateTransitions", ImmutableList.of(
                ImmutableMap.of(
                    "transitionTime", START_OF_TIME.toString(), "tldState", "PREDELEGATION"),
                ImmutableMap.of(
                    "transitionTime", now.toString(), "tldState", "SUNRISE"),
                ImmutableMap.of(
                    "transitionTime", now.plusMonths(1).toString(), "tldState", "SUNRISE")))));
    assertThat(result).isNotEqualTo(SUCCESS);

    // This is the same as the case above, but with a different ordering. It should still fail
    // regardless.
    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "tldStateTransitions", ImmutableList.of(
                ImmutableMap.of(
                    "transitionTime", now.plusMonths(1).toString(), "tldState", "SUNRISE"),
                ImmutableMap.of(
                    "transitionTime", now.toString(), "tldState", "SUNRUSH"),
                ImmutableMap.of(
                    "transitionTime", START_OF_TIME.toString(), "tldState", "PREDELEGATION")))));
    assertThat(result).isNotEqualTo(SUCCESS);

    // To foobar.
    Mockito.when(req.getRequestURI()).thenReturn("/_dr/admin/registry/foobar");

    // Test that all grace periods and other various status periods can be configured.
    clock.setTo(clock.nowUtc().plusMinutes(1));
    result = new RegistryServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", new ImmutableMap.Builder<String, String>()
        .put("addGracePeriod", Duration.standardSeconds(1).toString())
        .put("autoRenewGracePeriod", Duration.standardSeconds(2).toString())
        .put("redemptionGracePeriod", Duration.standardSeconds(3).toString())
        .put("renewGracePeriod", Duration.standardSeconds(4).toString())
        .put("transferGracePeriod", Duration.standardSeconds(5).toString())
        .put("automaticTransferLength", Duration.standardSeconds(6).toString())
        .put("pendingDeleteLength", Duration.standardSeconds(7).toString()).build()));
    assertThat(result).isEqualTo(SUCCESS);

    Registry loadedRegistry = Registry.get("foobar");
    assertThat(loadedRegistry.getAddGracePeriodLength()).isEqualTo(Duration.standardSeconds(1));
    assertThat(loadedRegistry.getAutoRenewGracePeriodLength())
        .isEqualTo(Duration.standardSeconds(2));
    assertThat(loadedRegistry.getRedemptionGracePeriodLength())
        .isEqualTo(Duration.standardSeconds(3));
    assertThat(loadedRegistry.getRenewGracePeriodLength()).isEqualTo(Duration.standardSeconds(4));
    assertThat(loadedRegistry.getTransferGracePeriodLength())
        .isEqualTo(Duration.standardSeconds(5));
    assertThat(loadedRegistry.getAutomaticTransferLength()).isEqualTo(Duration.standardSeconds(6));
    assertThat(loadedRegistry.getPendingDeleteLength()).isEqualTo(Duration.standardSeconds(7));
  }
}
