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

package google.registry.model.server;

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.ofy.Ofy.RECOMMENDED_MEMCACHE_EXPIRATION;

import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Unindex;
import google.registry.model.annotations.NotBackedUp;
import google.registry.model.annotations.NotBackedUp.Reason;
import google.registry.model.common.CrossTldSingleton;
import java.util.UUID;

/** A secret number used for generating tokens (such as XSRF tokens). */
@Entity
@Cache(expirationSeconds = RECOMMENDED_MEMCACHE_EXPIRATION)
@Unindex
@NotBackedUp(reason = Reason.AUTO_GENERATED)
public class ServerSecret extends CrossTldSingleton {

  private static ServerSecret secret;

  long mostSignificant;
  long leastSignificant;

  /**
   * Get the server secret, creating it if the datastore doesn't have one already.
   *
   * <p>There's a tiny risk of a race here if two calls to this happen simultaneously and create
   * different keys, in which case one of the calls will end up with an incorrect key. However, this
   * happens precisely once in the history of the system (after that it's always in datastore) so
   * it's not worth worrying about.
   */
  public static UUID getServerSecret() {
    if (secret == null) {
      secret = ofy().load().entity(new ServerSecret()).now();
    }
    if (secret == null) {
      secret = new ServerSecret();
      UUID uuid = UUID.randomUUID();
      secret.mostSignificant = uuid.getMostSignificantBits();
      secret.leastSignificant = uuid.getLeastSignificantBits();
      ofy().transact(new VoidWork(){
        @Override
        public void vrun() {
          ofy().saveWithoutBackup().entity(secret);
        }});
    }
    return new UUID(secret.mostSignificant, secret.leastSignificant);
  }
}
