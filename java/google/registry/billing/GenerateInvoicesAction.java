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

package google.registry.billing;

import static google.registry.request.Action.Method.POST;

import google.registry.request.Action;
import google.registry.request.auth.Auth;
import javax.inject.Inject;

/**
 * Generates invoices for the month and stores them on GCS.
 *
 * <p>Currently this is just a stub runner that verifies we can deploy dataflow jobs from App
 * Engine.
 */
@Action(
    path = GenerateInvoicesAction.PATH,
    method = POST,
    auth = Auth.AUTH_INTERNAL_ONLY
)
public class GenerateInvoicesAction implements Runnable {

  @Inject GenerateInvoicesAction() {}

  static final String PATH = "/_dr/task/generateInvoices";

  @Override
  public void run() {
    // MinWordCount minWordCount = new MinWordCount();
    // minWordCount.run();
  }
}
