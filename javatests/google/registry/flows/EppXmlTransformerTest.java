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


package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.util.ResourceUtils.readResourceBytes;

import google.registry.model.eppinput.EppInput;
import google.registry.model.eppoutput.EppOutput;
import google.registry.testing.ExceptionRule;
import google.registry.testing.ShardableTestCase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link EppXmlTransformer}. */
@RunWith(JUnit4.class)
public class EppXmlTransformerTest extends ShardableTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testUnmarshalingEppInput() throws Exception {
    EppInput input = unmarshal(
        EppInput.class, readResourceBytes(getClass(), "testdata/contact_info.xml").read());
    assertThat(input.getCommandName()).isEqualTo("Info");
  }

  @Test
  public void testUnmarshalingWrongClassThrows() throws Exception {
    thrown.expect(ClassCastException.class);
    EppXmlTransformer.unmarshal(
        EppOutput.class, readResourceBytes(getClass(), "testdata/contact_info.xml").read());
  }
}
