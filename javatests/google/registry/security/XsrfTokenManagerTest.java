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

package google.registry.security;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.base.Splitter;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeUserService;
import google.registry.testing.InjectRule;
import google.registry.testing.UserInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link XsrfTokenManager}. */
@RunWith(MockitoJUnitRunner.class)
public class XsrfTokenManagerTest {

  FakeClock clock = new FakeClock(START_OF_TIME);
  FakeUserService userService = new FakeUserService();

  XsrfTokenManager xsrfTokenManager = new XsrfTokenManager(clock, userService);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withUserService(UserInfo.createAdmin("a@example.com", "user1"))
      .build();

  @Rule
  public InjectRule inject = new InjectRule();

  @Before
  public void init() {
    // inject.setStaticField(XsrfTokenManager.class, "clock", clock);
  }

  @Test
  public void testSuccess() {
    assertThat(xsrfTokenManager.validateToken(xsrfTokenManager.generateToken("console"), "console"))
        .isTrue();
  }

  @Test
  public void testNoTimestamp() {
    assertThat(xsrfTokenManager.validateToken("foo", "console")).isFalse();
  }

  @Test
  public void testBadNumberTimestamp() {
    assertThat(xsrfTokenManager.validateToken("foo:bar", "console")).isFalse();
  }

  @Test
  public void testExpired() {
    String token = xsrfTokenManager.generateToken("console");
    clock.setTo(START_OF_TIME.plusDays(2));
    assertThat(xsrfTokenManager.validateToken(token, "console")).isFalse();
  }

  @Test
  public void testTimestampTamperedWith() {
    String encodedPart =
        Splitter.on(':').splitToList(xsrfTokenManager.generateToken("console")).get(0);
    long tamperedTimestamp = clock.nowUtc().plusMillis(1).getMillis();
    assertThat(xsrfTokenManager.validateToken(encodedPart + ":" + tamperedTimestamp, "console"))
        .isFalse();
  }

  @Test
  public void testDifferentUser() {
    assertThat(xsrfTokenManager
        .validateToken(xsrfTokenManager.generateToken("console", "b@example.com"), "console"))
            .isFalse();
  }

  @Test
  public void testDifferentScope() {
    assertThat(xsrfTokenManager.validateToken(xsrfTokenManager.generateToken("console"), "foobar"))
        .isFalse();
  }
}
