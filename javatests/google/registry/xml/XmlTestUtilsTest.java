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

package google.registry.xml;

import static google.registry.util.ResourceUtils.readResourceUtf8;
import static google.registry.xml.XmlTestUtils.assertXmlEquals;

import google.registry.testing.ExceptionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link XmlTestUtils}. */
@RunWith(JUnit4.class)
public class XmlTestUtilsTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  void runTest(String file1, String file2) throws Exception {
    String s1 = readResourceUtf8(getClass(), "testdata/" + file1);
    String s2 = readResourceUtf8(getClass(), "testdata/" + file2);
    assertXmlEquals(s1, s2);
  }

  @Test
  public void testSelfEquality() throws Exception {
    runTest("simple.xml", "simple.xml");
  }

  @Test
  public void testInequality() throws Exception {
    thrown.expect(AssertionError.class);
    runTest("simple.xml", "twoextensions_feeThenLaunch.xml");
  }

  @Test
  public void testMultipleElementsInDifferentNamespaces() throws Exception {
    runTest("twoextensions_feeThenLaunch.xml", "twoextensions_launchThenFee.xml");
  }

  @Test
  public void testMultipleElementsInDifferentNamespaces_differentValues() throws Exception {
    thrown.expect(AssertionError.class);
    runTest("twoextensions_feeThenLaunch.xml", "twoextensions_feeThenLaunch2.xml");
  }
}
