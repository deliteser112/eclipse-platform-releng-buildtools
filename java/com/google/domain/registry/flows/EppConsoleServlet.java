// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.flows;

import static com.google.appengine.api.users.UserServiceFactory.getUserService;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.domain.registry.flows.EppServletUtils.handleEppCommandAndWriteResponse;
import static java.lang.System.identityHashCode;

import com.google.appengine.api.users.User;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import com.google.domain.registry.flows.EppException.AuthenticationErrorException;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.security.XsrfProtectedServlet;

import org.joda.time.Duration;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** The {@link EppConsoleServlet} runs EPP from the console. It requires GAE user authentication. */
public class EppConsoleServlet extends XsrfProtectedServlet {

  /**
   * Credentials provided by the GAE User service.
   *
   * @see com.google.appengine.api.users.UserService
   */
  public static final class GaeUserCredentials implements TransportCredentials {

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

    final User gaeUser;

    @VisibleForTesting
    public GaeUserCredentials(@Nullable User gaeUser) {
      this.gaeUser = gaeUser;
    }

    @Override
    public boolean performsLoginCheck() {
      return true;
    }

    @Override
    public void validate(Registrar r) throws AuthenticationErrorException {
      if (gaeUser == null) {
        throw new UserNotLoggedInException();
      }
      // Allow admins to act as any registrar.
      if (getUserService().isUserAdmin()) {
        return;
      }
      // Check Registrar's contacts to see if any are associated with this gaeUserId.
      final String gaeUserId = gaeUser.getUserId();
      for (RegistrarContact rc : r.getContacts()) {
        if (gaeUserId.equals(rc.getGaeUserId())) {
          return;
        }
      }
      throw new BadGaeUserIdException(gaeUser);
    }

    @Override
    public String toString() {
      return String.format("GaeUserCredentials@%s{gaeUser: %s}", identityHashCode(this), gaeUser);
    }
  }

  /** Used by related UI servlets to generate matching XSRF tokens. */
  public static final String XSRF_SCOPE = "console";

  /** How long generated XSRF tokens for this scope remain valid. */
  public static final Duration XSRF_LIFETIME = Duration.standardDays(1);

  public EppConsoleServlet() {
    super(XSRF_SCOPE, false);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    handleEppCommandAndWriteResponse(
        ByteStreams.toByteArray(req.getInputStream()),
        rsp,
        new HttpSessionMetadata(
            new GaeUserCredentials(getUserService().getCurrentUser()), req.getSession(true)));
  }
}
