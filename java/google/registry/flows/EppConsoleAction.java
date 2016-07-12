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

package google.registry.flows;

import static com.google.appengine.api.users.UserServiceFactory.getUserService;

import google.registry.request.Action;
import google.registry.request.Action.Method;
import google.registry.request.Payload;
import javax.inject.Inject;
import javax.servlet.http.HttpSession;

/** Runs EPP from the console and requires GAE user authentication. */
@Action(
    path = "/registrar-xhr",
    xsrfProtection = true,
    xsrfScope = EppConsoleAction.XSRF_SCOPE,
    method = Method.POST)
public class EppConsoleAction implements Runnable {

  public static final String XSRF_SCOPE = "console";

  @Inject @Payload byte[] inputXmlBytes;
  @Inject HttpSession session;
  @Inject EppRequestHandler eppRequestHandler;
  @Inject EppConsoleAction() {}

  @Override
  public void run() {
    eppRequestHandler.executeEpp(
        new HttpSessionMetadata(session),
        new GaeUserCredentials(getUserService().getCurrentUser()),
        EppRequestSource.CONSOLE,
        false,  // This endpoint is never a dry run.
        false,  // This endpoint is never a superuser.
        inputXmlBytes);
  }
}
