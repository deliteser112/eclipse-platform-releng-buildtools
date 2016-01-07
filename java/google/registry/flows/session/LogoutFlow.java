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

package google.registry.flows.session;

import static google.registry.model.eppoutput.Result.Code.SUCCESS_AND_CLOSE;

import google.registry.flows.EppException;
import google.registry.flows.LoggedInFlow;
import google.registry.model.eppoutput.EppOutput;
import javax.inject.Inject;

/**
 * An EPP flow for logout.
 *
 * @error {@link google.registry.flows.LoggedInFlow.NotLoggedInException}
 */
public class LogoutFlow extends LoggedInFlow {

  @Inject LogoutFlow() {}

  @Override
  public final EppOutput run() throws EppException {
    sessionMetadata.invalidate();
    return createOutput(SUCCESS_AND_CLOSE);
  }
}
