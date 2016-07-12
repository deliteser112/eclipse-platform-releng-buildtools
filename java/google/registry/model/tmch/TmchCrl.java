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

package google.registry.model.tmch;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.ofy.Ofy.RECOMMENDED_MEMCACHE_EXPIRATION;

import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import google.registry.model.annotations.NotBackedUp;
import google.registry.model.annotations.NotBackedUp.Reason;
import google.registry.model.common.CrossTldSingleton;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.joda.time.DateTime;

/** Datastore singleton for ICANN's TMCH CA certificate revocation list (CRL). */
@Entity
@Cache(expirationSeconds = RECOMMENDED_MEMCACHE_EXPIRATION)
@Immutable
@NotBackedUp(reason = Reason.EXTERNALLY_SOURCED)
public final class TmchCrl extends CrossTldSingleton {

  String crl;
  DateTime updated;

  /** Returns the singleton instance of this entity, without memoization. */
  @Nullable
  public static TmchCrl get() {
    return ofy().load().entity(new TmchCrl()).now();
  }

  /**
   * Change the datastore singleton to a new ASCII-armored X.509 CRL.
   *
   * <p>Please do not call this function unless your CRL is properly formatted, signed by the root,
   * and actually newer than the one currently in the datastore.
   */
  public static void set(final String crl) {
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        TmchCrl tmchCrl = new TmchCrl();
        tmchCrl.updated = ofy().getTransactionTime();
        tmchCrl.crl = checkNotNull(crl, "crl");
        ofy().saveWithoutBackup().entity(tmchCrl);
      }});
  }

  /** ASCII-armored X.509 certificate revocation list. */
  public final String getCrl() {
    return crl;
  }

  /** Time we last updated the datastore with a newer ICANN CRL. */
  public final DateTime getUpdated() {
    return updated;
  }
}
