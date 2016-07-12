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

package google.registry.rdap;

import static com.google.common.truth.Truth.assertThat;

import google.registry.request.HttpException.UnprocessableEntityException;
import google.registry.testing.ExceptionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapSearchPattern}. */
@RunWith(JUnit4.class)
public class RdapSearchPatternTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testNoWildcards_ok() throws Exception {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("example.lol", true);
    assertThat(rdapSearchPattern.getInitialString()).isEqualTo("example.lol");
    assertThat(rdapSearchPattern.getHasWildcard()).isFalse();
    assertThat(rdapSearchPattern.getSuffix()).isNull();
  }

  @Test
  public void testWildcardNoTld_ok() throws Exception {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("exam*", true);
    assertThat(rdapSearchPattern.getInitialString()).isEqualTo("exam");
    assertThat(rdapSearchPattern.getHasWildcard()).isTrue();
    assertThat(rdapSearchPattern.getSuffix()).isNull();
  }

  @Test
  public void testWildcardTld_ok() throws Exception {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("exam*.lol", true);
    assertThat(rdapSearchPattern.getInitialString()).isEqualTo("exam");
    assertThat(rdapSearchPattern.getHasWildcard()).isTrue();
    assertThat(rdapSearchPattern.getSuffix()).isEqualTo("lol");
  }

  @Test
  public void testMultipleWildcards_unprocessable() throws Exception {
    thrown.expect(UnprocessableEntityException.class);
    RdapSearchPattern.create("ex*am*.lol", true);
  }

  @Test
  public void testWildcardNotAtEnd_unprocessable() throws Exception {
    thrown.expect(UnprocessableEntityException.class);
    RdapSearchPattern.create("ex*am", true);
  }

  @Test
  public void testWildcardNotAtEndWithTld_unprocessable() throws Exception {
    thrown.expect(UnprocessableEntityException.class);
    RdapSearchPattern.create("ex*am.lol", true);
  }

  @Test
  public void testPrefixTooShort_unprocessable() throws Exception {
    thrown.expect(UnprocessableEntityException.class);
    RdapSearchPattern.create("e*", true);
  }

  @Test
  public void testZeroLengthSuffix_unprocessable() throws Exception {
    thrown.expect(UnprocessableEntityException.class);
    RdapSearchPattern.create("exam*.", true);
  }

  @Test
  public void testDisallowedSuffix_unprocessable() throws Exception {
    thrown.expect(UnprocessableEntityException.class);
    RdapSearchPattern.create("exam*.lol", false);
  }

  @Test
  public void testNextInitialString_alpha() throws Exception {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("exam*.lol", true);
    assertThat(rdapSearchPattern.getNextInitialString()).isEqualTo("exan");
  }

  @Test
  public void testNextInitialString_unicode() throws Exception {
    RdapSearchPattern rdapSearchPattern = RdapSearchPattern.create("cat.みんな", true);
    assertThat(rdapSearchPattern.getNextInitialString()).isEqualTo("cat.みんに");
  }
}
