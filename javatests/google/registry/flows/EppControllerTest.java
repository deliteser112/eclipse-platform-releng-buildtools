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

import static google.registry.flows.EppXmlTransformer.marshal;

import google.registry.model.eppcommon.Trid;
import google.registry.model.eppoutput.Result;
import google.registry.model.eppoutput.Result.Code;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ShardableTestCase;
import google.registry.util.SystemClock;
import google.registry.xml.ValidationMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link EppController}. */
@RunWith(JUnit4.class)
public class EppControllerTest extends ShardableTestCase {

  @Rule
  public AppEngineRule appEngineRule = new AppEngineRule.Builder().build();

  @Test
  public void testMarshallingUnknownError() throws Exception {
    marshal(
        EppController.getErrorResponse(
            new SystemClock(), Result.create(Code.CommandFailed), Trid.create(null)),
        ValidationMode.STRICT);
  }
}
