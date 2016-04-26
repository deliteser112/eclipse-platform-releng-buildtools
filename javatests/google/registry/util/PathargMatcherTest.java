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

package google.registry.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unit tests for {@link PathargMatcher}. */
@RunWith(JUnit4.class)
public final class PathargMatcherTest {

  @Test
  public void testForPath_extractsPathargs() throws Exception {
    Pattern pattern = PathargMatcher.forPath("/task/:tld/:registrar");
    Matcher matcher = pattern.matcher("/task/soy/TheRegistrar");
    assertThat(matcher.matches()).isTrue();
    assertThat(matcher.group("tld")).isEqualTo("soy");
    assertThat(matcher.group("registrar")).isEqualTo("TheRegistrar");
  }

  @Test
  public void testForPath_missingPatharg_failure() throws Exception {
    Pattern pattern = PathargMatcher.forPath("/task/:tld/:registrar");
    assertThat(pattern.matcher("/task/soy").matches()).isFalse();
    assertThat(pattern.matcher("/task/soy/").matches()).isFalse();
  }

  @Test
  public void testForPath_extraSlash_failure() throws Exception {
    Pattern pattern = PathargMatcher.forPath("/task/:tld/:registrar");
    assertThat(pattern.matcher("/task/soy/TheRegistrar/").matches()).isFalse();
  }

  @Test
  public void testForPath_extraPatharg_failure() throws Exception {
    Pattern pattern = PathargMatcher.forPath("/task/:tld/:registrar");
    assertThat(pattern.matcher("/task/soy/TheRegistrar/blob").matches()).isFalse();
  }

  @Test
  public void testForPath_emptyPatharg_failure() throws Exception {
    Pattern pattern = PathargMatcher.forPath("/task/:tld/:registrar");
    assertThat(pattern.matcher("/task//TheRegistrar").matches()).isFalse();
  }

  @Test
  public void testForPath_badFront_failure() throws Exception {
    Pattern pattern = PathargMatcher.forPath("/task/:tld/:registrar");
    assertThat(pattern.matcher("/dog/soy/TheRegistrar").matches()).isFalse();
  }

  @Test
  public void testForPath_badCasing_failure() throws Exception {
    Pattern pattern = PathargMatcher.forPath("/task/:tld/:registrar");
    assertThat(pattern.matcher("/TASK/soy/TheRegistrar").matches()).isFalse();
  }
}
