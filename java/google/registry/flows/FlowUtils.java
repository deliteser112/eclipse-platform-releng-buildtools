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

import google.registry.flows.EppException.CommandUseErrorException;

/** Static utility functions for flows. */
public final class FlowUtils {

  private FlowUtils() {}

  /** Validate that there is a logged in client. */
  public static void validateClientIsLoggedIn(String clientId) throws EppException {
    if (clientId.isEmpty()) {
      throw new NotLoggedInException();
    }
  }

  /** Registrar is not logged in. */
  public static class NotLoggedInException extends CommandUseErrorException {
    public NotLoggedInException() {
      super("Registrar is not logged in.");
    }
  }
}
