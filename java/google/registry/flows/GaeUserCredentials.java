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

package google.registry.flows;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Strings.nullToEmpty;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.common.annotations.VisibleForTesting;
import google.registry.flows.EppException.AuthenticationErrorException;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import javax.annotation.Nullable;

/** Credentials provided by {@link com.google.appengine.api.users.UserService}. */
public class GaeUserCredentials implements TransportCredentials {

  private final User gaeUser;
  private final Boolean isAdmin;

  /**
   * Create an instance for the current user, as determined by {@code UserService}.
   *
   * <p>Note that the current user may be null (i.e. there is no logged in user).
   */
  public static GaeUserCredentials forCurrentUser(UserService userService) {
    User user = userService.getCurrentUser();
    return new GaeUserCredentials(user, user != null ? userService.isUserAdmin() : null);
  }

  /** Create an instance that represents an explicit user (for testing purposes). */
  @VisibleForTesting
  public static GaeUserCredentials forTestingUser(User gaeUser, Boolean isAdmin) {
    checkArgumentNotNull(gaeUser);
    checkArgumentNotNull(isAdmin);
    return new GaeUserCredentials(gaeUser, isAdmin);
  }

  /** Create an instance that represents a non-logged in user (for testing purposes). */
  @VisibleForTesting
  public static GaeUserCredentials forLoggedOutUser() {
    return new GaeUserCredentials(null, null);
  }

  private GaeUserCredentials(@Nullable User gaeUser, @Nullable Boolean isAdmin) {
    this.gaeUser = gaeUser;
    this.isAdmin = isAdmin;
  }

  @VisibleForTesting
  User getUser() {
    return gaeUser;
  }

  @Override
  public void validate(Registrar registrar, String ignoredPassword)
      throws AuthenticationErrorException {
    if (gaeUser == null) {
      throw new UserNotLoggedInException();
    }
    // Allow admins to act as any registrar.
    if (Boolean.TRUE.equals(isAdmin)) {
      return;
    }
    // Check Registrar's contacts to see if any are associated with this gaeUserId.
    final String gaeUserId = gaeUser.getUserId();
    for (RegistrarContact rc : registrar.getContacts()) {
      if (gaeUserId.equals(rc.getGaeUserId())) {
        return;
      }
    }
    throw new BadGaeUserIdException(gaeUser);
  }

  @Override
  public String toString() {
    return toStringHelper(getClass())
        .add("gaeUser", gaeUser)
        .add("isAdmin", isAdmin)
        .toString();
  }

  /** User is not logged in as a GAE user. */
  public static class UserNotLoggedInException extends AuthenticationErrorException {
    public UserNotLoggedInException() {
      super("User is not logged in");
    }
  }

  /** GAE user id is not allowed to login as requested registrar. */
  public static class BadGaeUserIdException extends AuthenticationErrorException {
    public BadGaeUserIdException(User user) {
      super(
          "User id is not allowed to login as requested registrar: "
              + (nullToEmpty(user.getEmail())));
    }
  }
}
