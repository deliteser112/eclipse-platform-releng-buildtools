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

package google.registry.flows;

import static com.google.common.base.MoreObjects.toStringHelper;

import google.registry.flows.EppException.AuthenticationErrorException;
import google.registry.model.registrar.Registrar;
import google.registry.request.auth.AuthenticatedRegistrarAccessor;
import google.registry.request.auth.AuthenticatedRegistrarAccessor.RegistrarAccessDeniedException;

/** Credentials provided by {@link com.google.appengine.api.users.UserService}. */
public class GaeUserCredentials implements TransportCredentials {

  private final AuthenticatedRegistrarAccessor registrarAccessor;

  public GaeUserCredentials(AuthenticatedRegistrarAccessor registrarAccessor) {
    this.registrarAccessor = registrarAccessor;
  }

  @Override
  public void validate(Registrar registrar, String ignoredPassword)
      throws AuthenticationErrorException {
    try {
      registrarAccessor.verifyAccess(registrar.getClientId());
    } catch (RegistrarAccessDeniedException e) {
      throw new UserForbiddenException(e);
    }
  }

  @Override
  public String toString() {
    return toStringHelper(getClass()).add("user", registrarAccessor.userIdForLogging()).toString();
  }

  /** GAE User can't access the requested registrar. */
  public static class UserForbiddenException extends AuthenticationErrorException {
    public UserForbiddenException(RegistrarAccessDeniedException e) {
      super(e.getMessage());
    }
  }
}
