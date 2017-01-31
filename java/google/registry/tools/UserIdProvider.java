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

package google.registry.tools;


/** Static methods to get the current user id. */
class UserIdProvider {

  static String getTestUserId() {
    return "test@example.com";  // Predefined default user for the development server.
  }

  /** Pick up the username from an appropriate source. */
  static String getProdUserId() {
    // TODO(b/28219927): fix tool authentication to use actual user credentials.
    // For the time being, use the empty string so that for testing, requests without credentials
    // can still pass the server-side XSRF token check (which will represent no user as "").
    return "";
  }
}
