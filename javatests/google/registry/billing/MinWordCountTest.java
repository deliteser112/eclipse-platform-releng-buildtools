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

package google.registry.billing;

import static com.google.common.truth.Truth.assertThat;

import google.registry.billing.MinWordCount.ExtractWordsFn;
import java.util.List;
import org.apache.beam.sdk.transforms.DoFnTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link MinWordCount}*/
@RunWith(JUnit4.class)
public class MinWordCountTest {

  @Test
  public void testDebuggingWordCount() throws Exception {
    ExtractWordsFn extractWordsFn = new ExtractWordsFn();
    DoFnTester<String, String> fntester = DoFnTester.of(extractWordsFn);
    String testInput = "hi there\nwhats up\nim good thanks";
    List<String> outputs = fntester.processBundle(testInput);
    assertThat(outputs).containsExactly("hi", "there", "whats", "up", "im", "good", "thanks");
  }
}
