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

package google.registry.model.common;

import static google.registry.model.ofy.ObjectifyService.allocateId;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.users.User;
import com.google.common.base.Splitter;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import google.registry.model.ImmutableObject;
import google.registry.model.annotations.NotBackedUp;
import google.registry.model.annotations.NotBackedUp.Reason;

/**
 * A helper class to convert email addresses to GAE user ids. It does so by persisting a User
 * object with the email address to datastore, and then immediately reading it back.
 */
@Entity
@NotBackedUp(reason = Reason.TRANSIENT)
public class GaeUserIdConverter extends ImmutableObject {

  @Id
  public long id;

  User user;

  /**
   * Converts an email address to a GAE user id.
   *
   * @return Numeric GAE user id (in String form), or null if email address has no GAE id
   */
  public static String convertEmailAddressToGaeUserId(String emailAddress) {
    final GaeUserIdConverter gaeUserIdConverter = new GaeUserIdConverter();
    gaeUserIdConverter.id = allocateId();
    gaeUserIdConverter.user =
        new User(emailAddress, Splitter.on('@').splitToList(emailAddress).get(1));

    try {
      // Perform these operations in a transactionless context to avoid enlisting in some outer
      // transaction (if any).
      ofy().doTransactionless(new VoidWork() {
        @Override
        public void vrun() {
          ofy().saveWithoutBackup().entity(gaeUserIdConverter).now();
        }});

      // The read must be done in its own transaction to avoid reading from the session cache.
      return ofy().transactNew(new Work<String>() {
        @Override
        public String run() {
          return ofy().load().entity(gaeUserIdConverter).safe().user.getUserId();
        }});
    } finally {
      ofy().doTransactionless(new VoidWork() {
        @Override
        public void vrun() {
          ofy().deleteWithoutBackup().entity(gaeUserIdConverter).now();
        }});
    }
  }
}
