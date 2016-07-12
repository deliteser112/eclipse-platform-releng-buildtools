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

package google.registry.tmch;

import static com.google.common.base.Verify.verifyNotNull;
import static google.registry.util.UrlFetchUtils.setAuthorizationHeader;

import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.common.base.Optional;
import google.registry.keyring.api.KeyModule.Key;
import google.registry.model.registry.Registry;
import javax.inject.Inject;

/** Helper class for setting the authorization header on a MarksDB LORDN request. */
final class LordnRequestInitializer {

  @Inject @Key("marksdbLordnPassword") Optional<String> marksdbLordnPassword;
  @Inject LordnRequestInitializer() {}

  /** Initializes a URL fetch request for talking to the MarksDB server. */
  void initialize(HTTPRequest request, String tld) {
    setAuthorizationHeader(request, getMarksDbLordnCredentials(tld));
  }

  /** Returns the username and password for the current TLD to login to the MarksDB server. */
  private Optional<String> getMarksDbLordnCredentials(String tld) {
    if (marksdbLordnPassword.isPresent()) {
      String lordnUsername = verifyNotNull(Registry.get(tld).getLordnUsername(),
          "lordnUsername is not set for %s.", Registry.get(tld).getTld());
      return Optional.of(String.format("%s:%s", lordnUsername, marksdbLordnPassword.get()));
    } else {
      return Optional.absent();
    }
  }
}
