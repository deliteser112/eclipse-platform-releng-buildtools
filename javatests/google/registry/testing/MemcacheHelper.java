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

package google.registry.testing;

import static com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig.getLocalMemcacheService;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushRequest;
import com.google.appengine.tools.development.LocalRpcService;
import com.googlecode.objectify.Key;

/** Utilities for manipulating memcache in tests. */
public class MemcacheHelper {

  /** Clear out memcache */
  public static void clearMemcache() {
    getLocalMemcacheService().flushAll(
        new LocalRpcService.Status(), MemcacheFlushRequest.newBuilder().build());
  }

  /** Clears memcache, inserts the specific keys requested, and clears the session cache. */
  public static void setMemcacheContents(Key<?>... keys) {
    // Clear out the session cache. This is needed so that the calls to loadWithMemcache() below
    // actually go to datastore and write to memcache instead of bottoming out at the session cache.
    ofy().clearSessionCache();
    clearMemcache();
    // Load the entities we want to be in memcache. If an entity's type is not marked with @Cache it
    // will be ignored.
    ofy().loadWithMemcache().keys(keys).values();
    // Clear out the session cache again, since it now contains the newly loaded types.
    ofy().clearSessionCache();
  }
}
