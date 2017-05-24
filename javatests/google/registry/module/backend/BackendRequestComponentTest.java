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

package google.registry.module.backend;

import static com.google.common.io.Resources.getResource;

import google.registry.request.RouterDisplayHelper;
import google.registry.testing.GoldenFileTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BackendRequestComponent}. */
@RunWith(JUnit4.class)
public class BackendRequestComponentTest {

  @Test
  public void testRoutingMap() throws Exception {
    GoldenFileTestHelper.testGoldenFile(
        RouterDisplayHelper.extractHumanReadableRoutesFromComponent(BackendRequestComponent.class),
        getResource(BackendRequestComponentTest.class, "testdata/backend_routing.txt"),
        "backend routing map",
        "get_routing_map -c " + BackendRequestComponent.class.getName());
  }
}
