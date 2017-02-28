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

import com.google.appengine.api.users.User;
import com.google.common.base.Splitter;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeUserService;
import google.registry.testing.InjectRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link XsrfTokenManager}. */
@RunWith(MockitoJUnitRunner.class)
public class XsrfTokenManagerTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public InjectRule inject = new InjectRule();

  private final User testUser = new User("test@example.com", "test@example.com");
  private final FakeClock clock = new FakeClock(START_OF_TIME);
  private final FakeUserService userService = new FakeUserService();
  private final XsrfTokenManager xsrfTokenManager = new XsrfTokenManager(clock, userService);

  String realToken;

  @Before
  public void init() {
    userService.setUser(testUser, false);
    realToken = xsrfTokenManager.generateToken("console", testUser.getEmail());
  }

  @Test
  public void testSuccess() {
    assertThat(xsrfTokenManager.validateToken(realToken, "console")).isTrue();
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
    clock.setTo(START_OF_TIME.plusDays(2));
    assertThat(xsrfTokenManager.validateToken(realToken, "console")).isFalse();
  }

  @Test
  public void testTimestampTamperedWith() {
    String encodedPart = Splitter.on(':').splitToList(realToken).get(0);
    long tamperedTimestamp = clock.nowUtc().plusMillis(1).getMillis();
    assertThat(xsrfTokenManager.validateToken(encodedPart + ":" + tamperedTimestamp, "console"))
        .isFalse();
  }

  @Test
  public void testDifferentUser() {
    assertThat(xsrfTokenManager
        .validateToken(xsrfTokenManager.generateToken("console", "eve@example.com"), "console"))
            .isFalse();
  }

  @Test
  public void testDifferentScope() {
    assertThat(xsrfTokenManager.validateToken(realToken, "foobar")).isFalse();
  }

  @Test
  public void testNullScope() {
    String tokenWithNullScope = xsrfTokenManager.generateToken(null, testUser.getEmail());
    assertThat(xsrfTokenManager.validateToken(tokenWithNullScope, null)).isTrue();
  }

  // This test checks that the server side will pass when we switch the client to use a null scope.
  @Test
  public void testNullScopePassesWhenTestedWithNonNullScope() {
    String tokenWithNullScope = xsrfTokenManager.generateToken(null, testUser.getEmail());
    assertThat(xsrfTokenManager.validateToken(tokenWithNullScope, "console")).isTrue();
  }
}
