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

package google.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.JUnitBackports.assertThrows;

import google.registry.request.HttpException.UnprocessableEntityException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapSearchPattern}. */
@RunWith(JUnit4.class)
public class RdapSearchPatternTest {

  @Test
  public void testNoWildcards_ok() {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("example.lol", true);
    assertThat(rdapSearchPattern.getInitialString()).isEqualTo("example.lol");
    assertThat(rdapSearchPattern.getHasWildcard()).isFalse();
    assertThat(rdapSearchPattern.getSuffix()).isNull();
  }

  @Test
  public void testWildcardNoTld_ok() {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("exam*", true);
    assertThat(rdapSearchPattern.getInitialString()).isEqualTo("exam");
    assertThat(rdapSearchPattern.getHasWildcard()).isTrue();
    assertThat(rdapSearchPattern.getSuffix()).isNull();
  }

  @Test
  public void testWildcardTld_ok() {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("exam*.lol", true);
    assertThat(rdapSearchPattern.getInitialString()).isEqualTo("exam");
    assertThat(rdapSearchPattern.getHasWildcard()).isTrue();
    assertThat(rdapSearchPattern.getSuffix()).isEqualTo("lol");
  }

  @Test
  public void testMultipleWildcards_unprocessable() {
    assertThrows(
        UnprocessableEntityException.class, () -> RdapSearchPattern.create("ex*am*.lol", true));
  }

  @Test
  public void testWildcardNotAtEnd_unprocessable() {
    assertThrows(UnprocessableEntityException.class, () -> RdapSearchPattern.create("ex*am", true));
  }

  @Test
  public void testWildcardNotAtEndWithTld_unprocessable() {
    assertThrows(
        UnprocessableEntityException.class, () -> RdapSearchPattern.create("ex*am.lol", true));
  }

  @Test
  public void testShortString_ok() {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("e", true);
    assertThat(rdapSearchPattern.getInitialString()).isEqualTo("e");
    assertThat(rdapSearchPattern.getHasWildcard()).isFalse();
    assertThat(rdapSearchPattern.getSuffix()).isNull();
  }

  @Test
  public void testZeroLengthSuffix_unprocessable() {
    assertThrows(
        UnprocessableEntityException.class, () -> RdapSearchPattern.create("exam*.", true));
  }

  @Test
  public void testDisallowedSuffix_unprocessable() {
    assertThrows(
        UnprocessableEntityException.class, () -> RdapSearchPattern.create("exam*.lol", false));
  }

  @Test
  public void testNextInitialString_alpha() {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("exam*.lol", true);
    assertThat(rdapSearchPattern.getNextInitialString()).isEqualTo("exan");
  }

  @Test
  public void testNextInitialString_unicode() {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("cat.みんな", true);
    assertThat(rdapSearchPattern.getNextInitialString()).isEqualTo("cat.みんに");
  }
}
