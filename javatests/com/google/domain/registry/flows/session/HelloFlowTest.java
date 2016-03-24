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

package com.google.domain.registry.flows.session;

import static com.google.domain.registry.flows.EppXmlTransformer.marshal;
import static com.google.domain.registry.xml.ValidationMode.STRICT;
import static com.google.domain.registry.xml.XmlTestUtils.assertXmlEquals;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.domain.registry.flows.FlowTestCase;

import org.junit.Test;

/** Unit tests for {@link HelloFlow}. */
public class HelloFlowTest extends FlowTestCase<HelloFlow> {
  @Test
  public void testHello() throws Exception {
    setEppInput("hello.xml");
    assertTransactionalFlow(false);
    assertXmlEquals(readFile("greeting_crr.xml"), new String(marshal(runFlow(), STRICT), UTF_8),
        "epp.greeting.svDate");
  }

  // Extra methods so the test runner doesn't produce empty shards.

  @Test
  public void testNothing1() {}

  @Test
  public void testNothing2() {}

  @Test
  public void testNothing3() {}
}
