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

import static com.google.common.base.Preconditions.checkState;

import javax.servlet.http.HttpSession;

/** A metadata class that is a wrapper around {@link HttpSession}. */
public class HttpSessionMetadata extends SessionMetadata {

  private final HttpSession session;
  private boolean isValid = true;

  public HttpSessionMetadata(TransportCredentials credentials, HttpSession session) {
    this.session = session;
    setTransportCredentials(credentials);
  }

  @Override
  protected void checkValid() {
    checkState(isValid, "This session has been invalidated.");
  }

  @Override
  public void invalidate() {
    session.invalidate();
    isValid = false;
  }

  @Override
  protected void setProperty(String key, Object value) {
    if (value == null) {
      session.removeAttribute(key);
    } else {
      session.setAttribute(key, value);
    }
  }

  @Override
  protected Object getProperty(String key) {
    return session.getAttribute(key);
  }

  @Override
  public SessionSource getSessionSource() {
    return SessionSource.HTTP;
  }
}
