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

package com.google.domain.registry.loadtest;

import static com.google.cloud.sql.jdbc.internal.Charsets.UTF_8;
import static com.google.domain.registry.flows.EppController.handleEppCommand;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.domain.registry.flows.SessionMetadata.SessionSource;
import com.google.domain.registry.flows.StatelessRequestSessionMetadata;
import com.google.domain.registry.model.eppcommon.ProtocolDefinition;

/** A task that executes a single EPP command. */
public class LoadTask implements DeferredTask {

  private static final long serialVersionUID = -7541344556045675149L;

  private final String clientId;
  private final String xml;

  LoadTask(String clientId, String xml) {
    this.clientId = clientId;
    this.xml = xml;
  }

  @Override
  public void run() {
    handleEppCommand(
        xml.getBytes(UTF_8),
        new StatelessRequestSessionMetadata(
            clientId,
            false,
            false,
            ProtocolDefinition.getVisibleServiceExtensionUris(),
            SessionSource.LOADTEST));
  }
}
