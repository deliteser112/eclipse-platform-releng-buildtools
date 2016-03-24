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

import java.util.Set;

/** A read-only {@link SessionMetadata} that doesn't support login/logout. */
public class StatelessRequestSessionMetadata extends SessionMetadata {

  private final String clientId;
  private final boolean isSuperuser;
  private final boolean isDryRun;
  private final Set<String> serviceExtensionUris;
  private final SessionSource sessionSource;

  public StatelessRequestSessionMetadata(
      String clientId,
      boolean isSuperuser,
      boolean isDryRun,
      Set<String> serviceExtensionUris,
      SessionSource source) {
    this.clientId = clientId;
    this.isSuperuser = isSuperuser;
    this.isDryRun = isDryRun;
    this.serviceExtensionUris = serviceExtensionUris;
    this.sessionSource = source;
  }

  @Override
  public String getClientId() {
    return clientId;
  }

  @Override
  public boolean isSuperuser() {
    return isSuperuser;
  }

  @Override
  public boolean isDryRun() {
    return isDryRun;
  }

  @Override
  public Set<String> getServiceExtensionUris() {
    return serviceExtensionUris;
  }

  @Override
  public SessionSource getSessionSource() {
    return sessionSource;
  }

  @Override
  public void invalidate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setTransportCredentials(TransportCredentials credentials) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void setProperty(String key, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected Object getProperty(String key) {
    // We've overridden the getters of all of the properties that we care about. Return null for
    // everything else so that toString() continues to work.
    return null;
  }
}
