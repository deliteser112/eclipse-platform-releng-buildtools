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

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import java.util.Set;
import javax.annotation.Nullable;

// TODO: Consider reconciling this with AppEngineRule.withUserService()

/** Fake implementation of {@link UserService} for testing. */
public class FakeUserService implements UserService {

  @Nullable private User user = null;
  private boolean isAdmin = false;

  public void setUser(@Nullable User user, boolean isAdmin) {
    this.user = user;
    this.isAdmin = isAdmin;
  }

  @Override
  public String createLoginURL(String destinationURL) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String createLoginURL(String destinationURL, String authDomain) {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public String createLoginURL(String destinationURL, String authDomain, String federatedIdentity,
      Set<String> attributesRequest) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String createLogoutURL(String destinationURL) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String createLogoutURL(String destinationURL, String authDomain) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isUserLoggedIn() {
    return user != null;
  }

  @Override
  public boolean isUserAdmin() {
    return isAdmin;
  }

  @Override
  public User getCurrentUser() {
    return user;
  }
}
