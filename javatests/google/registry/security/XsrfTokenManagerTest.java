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

package google.registry.security;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.security.XsrfTokenManager.generateToken;
import static google.registry.security.XsrfTokenManager.validateToken;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.base.Splitter;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.UserInfo;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link XsrfTokenManager}. */
@RunWith(MockitoJUnitRunner.class)
public class XsrfTokenManagerTest {

  private static final Duration ONE_DAY = Duration.standardDays(1);

  FakeClock clock = new FakeClock(START_OF_TIME);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withUserService(UserInfo.createAdmin("a@example.com", "user1"))
      .build();

  @Rule
  public InjectRule inject = new InjectRule();

  @Before
  public void init() {
    inject.setStaticField(XsrfTokenManager.class, "clock", clock);
  }

  @Test
  public void testSuccess() {
    assertThat(validateToken(generateToken("console"), "console", ONE_DAY)).isTrue();
  }

  @Test
  public void testNoTimestamp() {
    assertThat(validateToken("foo", "console", ONE_DAY)).isFalse();
  }

  @Test
  public void testBadNumberTimestamp() {
    assertThat(validateToken("foo:bar", "console", ONE_DAY)).isFalse();
  }

  @Test
  public void testExpired() {
    String token = generateToken("console");
    clock.setTo(START_OF_TIME.plusDays(2));
    assertThat(validateToken(token, "console", ONE_DAY)).isFalse();
  }

  @Test
  public void testTimestampTamperedWith() {
    String encodedPart = Splitter.on(':').splitToList(generateToken("console")).get(0);
    long tamperedTimestamp = clock.nowUtc().plusMillis(1).getMillis();
    assertThat(validateToken(encodedPart + ":" + tamperedTimestamp, "console", ONE_DAY)).isFalse();
  }

  @Test
  public void testDifferentUser() {
    assertThat(validateToken(generateToken("console", "b@example.com"), "console", ONE_DAY))
        .isFalse();
  }

  @Test
  public void testDifferentScope() {
    assertThat(validateToken(generateToken("console"), "foobar", ONE_DAY)).isFalse();
  }
}
