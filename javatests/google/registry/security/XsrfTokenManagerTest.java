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
import static org.junit.Assert.fail;

import com.google.appengine.api.users.User;
import com.google.common.base.Splitter;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeUserService;
import google.registry.testing.InjectRule;
import org.joda.time.Duration;
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

  private String token;
  private String legacyToken;

  @Before
  public void init() {
    userService.setUser(testUser, false);
    token = xsrfTokenManager.generateToken(testUser.getEmail());
    legacyToken = xsrfTokenManager.generateLegacyToken("console", testUser.getEmail());
  }

  @Test
  public void testGenerateLegacyToken_invalidScope() {
    try {
      xsrfTokenManager.generateLegacyToken("foo", testUser.getEmail());
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo("Invalid scope value: foo");
    }
  }

  @Test
  public void testValidate_token() {
    assertThat(xsrfTokenManager.validateToken(token)).isTrue();
  }

  @Test
  public void testValidate_legacyToken() {
    assertThat(xsrfTokenManager.validateToken(legacyToken)).isTrue();
  }

  @Test
  public void testValidate_token_missingParts() {
    assertThat(xsrfTokenManager.validateToken("foo")).isFalse();
  }

  @Test
  public void testValidate_token_badNumberTimestamp() {
    assertThat(xsrfTokenManager.validateToken("1:notanumber:base64")).isFalse();
  }

  @Test
  public void testValidate_legacyToken_badNumberTimestamp() {
    assertThat(xsrfTokenManager.validateToken("base64:notanumber")).isFalse();
  }

  @Test
  public void testValidate_token_expiresAfterOneDay() {
    clock.advanceBy(Duration.standardDays(1));
    assertThat(xsrfTokenManager.validateToken(token)).isTrue();
    clock.advanceOneMilli();
    assertThat(xsrfTokenManager.validateToken(token)).isFalse();
  }

  @Test
  public void testValidate_legacyToken_expiresAfterOneDay() {
    clock.advanceBy(Duration.standardDays(1));
    assertThat(xsrfTokenManager.validateToken(legacyToken)).isTrue();
    clock.advanceOneMilli();
    assertThat(xsrfTokenManager.validateToken(legacyToken)).isFalse();
  }

  @Test
  public void testValidate_token_timestampTamperedWith() {
    String encodedPart = Splitter.on(':').splitToList(token).get(2);
    long fakeTimestamp = clock.nowUtc().plusMillis(1).getMillis();
    assertThat(xsrfTokenManager.validateToken("1:" + fakeTimestamp + ":" + encodedPart)).isFalse();
  }

  @Test
  public void testValidate_legacyToken_timestampTamperedWith() {
    String encodedPart = Splitter.on(':').splitToList(legacyToken).get(0);
    long tamperedTimestamp = clock.nowUtc().plusMillis(1).getMillis();
    assertThat(xsrfTokenManager.validateToken(encodedPart + ":" + tamperedTimestamp)).isFalse();
  }

  @Test
  public void testValidate_token_differentUser() {
    String otherToken = xsrfTokenManager.generateToken("eve@example.com");
    assertThat(xsrfTokenManager.validateToken(otherToken)).isFalse();
  }

  @Test
  public void testValidate_legacyToken_differentUser() {
    String otherToken = xsrfTokenManager.generateLegacyToken("console", "eve@example.com");
    assertThat(xsrfTokenManager.validateToken(otherToken)).isFalse();
  }

  @Test
  public void testValidate_legacyToken_adminScope() {
    String adminToken = xsrfTokenManager.generateLegacyToken("admin", testUser.getEmail());
    assertThat(xsrfTokenManager.validateToken(adminToken)).isTrue();
  }

  @Test
  public void testValidate_legacyToken_consoleScope() {
    String consoleToken = xsrfTokenManager.generateLegacyToken("console", testUser.getEmail());
    assertThat(xsrfTokenManager.validateToken(consoleToken)).isTrue();
  }
}
