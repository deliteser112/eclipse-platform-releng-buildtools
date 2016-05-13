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

package com.google.domain.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.assertNoBillingEvents;

import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.eppoutput.CheckData;

/**
 * Base class for resource check flow unit tests.
 *
 * @param <F> the flow type
 * @param <R> the resource type
 */
public class ResourceCheckFlowTestCase<F extends Flow, R extends EppResource>
    extends ResourceFlowTestCase<F, R> {

  protected void doCheckTest(CheckData.Check... expected) throws Exception {
    assertTransactionalFlow(false);
    assertThat(expected).asList().containsExactlyElementsIn(
        ((CheckData) runFlow().getResponse().getResponseData().get(0)).getChecks());
    assertNoHistory();  // Checks don't create a history event.
    assertNoBillingEvents();  // Checks are always free.
  }
}
