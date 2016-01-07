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

package google.registry.testing;

import org.junit.Test;

/**
 * Test case with 3 empty methods.
 *
 * The sharding test runner fails if it produces an empty shard, and we shard 4 ways. This makes
 * sure that we never produces empty shards.
 */
public abstract class ShardableTestCase {

  @Test
  public void testNothing1() {}

  @Test
  public void testNothing2() {}

  @Test
  public void testNothing3() {}
}
