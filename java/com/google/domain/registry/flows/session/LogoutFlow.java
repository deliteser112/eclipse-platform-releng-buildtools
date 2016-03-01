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

package com.google.domain.registry.flows.session;

import static com.google.domain.registry.model.eppoutput.Result.Code.SuccessAndClose;

import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.LoggedInFlow;
import com.google.domain.registry.model.eppoutput.EppOutput;

/**
 * An EPP flow for logout.
 *
 * @error {@link com.google.domain.registry.flows.LoggedInFlow.NotLoggedInException}
 */
public class LogoutFlow extends LoggedInFlow {
  @Override
  public final EppOutput run() throws EppException {
    sessionMetadata.invalidate();
    return createOutput(SuccessAndClose);
  }
}
