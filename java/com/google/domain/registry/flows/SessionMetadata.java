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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.domain.registry.util.CollectionUtils.nullToEmpty;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;

import java.util.Set;

/** Class to allow setting and retrieving session information in flows. */
public abstract class SessionMetadata {

  /**
   * An enum that identifies the origin of the session.
   */
  public enum SessionSource {
    /** e.g. {@code EppConsoleServlet} */
    CONSOLE,

    /** e.g. {@code EppTlsServlet} */
    HTTP,

    /** e.g. {@code EppToolServlet} */
    TOOL,

    /** e.g. {@code LoadTestAction} */
    LOADTEST,

    /** Direct flow runs (default for e.g. testing) */
    NONE
  }

  private TransportCredentials credentials;

   /** The key used for looking up the current client id on the session object. */
  protected static final String CLIENT_ID_KEY = "CLIENT_ID";

  /** The key used for looking up the superuser bit on the session object. */
  protected static final String SUPERUSER_KEY = "SUPERUSER";

  /** The key used for looking up the service extensions on the session object. */
  protected static final String SERVICE_EXTENSIONS_KEY = "SERVICE_EXTENSIONS";

  /** The key used for looking up the number of failed login attempts. */
  protected static final String FAILED_LOGIN_ATTEMPTS_KEY = "FAILED_LOGIN_ATTEMPTS";

  protected abstract void setProperty(String key, Object value);

  protected abstract Object getProperty(String key);

  /**
   * Invalidates the session. A new instance must be created after this for future sessions.
   * Attempts to invoke methods of this class after this method has been called will throw
   * {@code IllegalStateException}.
   */
  public abstract void invalidate();

  /** Subclasses can override this to verify that this is a valid session. */
  protected void checkValid() {}

  protected void setPropertyChecked(String key, Object value) {
    checkValid();
    setProperty(key, value);
  }

  @SuppressWarnings("unchecked")
  protected <T> T getPropertyChecked(String key) {
    checkValid();
    return (T) getProperty(key);
  }

  public TransportCredentials getTransportCredentials() {
    checkValid();
    return credentials;
  }

  public void setTransportCredentials(TransportCredentials credentials) {
    checkValid();
    this.credentials = credentials;
  }

  public String getClientId() {
    return getPropertyChecked(CLIENT_ID_KEY);
  }

  public boolean isSuperuser() {
    return Boolean.TRUE.equals(getPropertyChecked(SUPERUSER_KEY));
  }

  public Set<String> getServiceExtensionUris() {
    return getPropertyChecked(SERVICE_EXTENSIONS_KEY);
  }

  public abstract SessionSource getSessionSource();

  /**
   * Subclasses can override if they present a need to change the session
   * source at runtime (e.g. anonymous classes created for testing)
   */
  public void setSessionSource(@SuppressWarnings("unused") SessionSource source) {
    throw new UnsupportedOperationException();
  }

  public void setClientId(String clientId) {
    setPropertyChecked(CLIENT_ID_KEY, clientId);
  }

  public void setSuperuser(boolean superuser) {
    setPropertyChecked(SUPERUSER_KEY, superuser);
  }

  public void setServiceExtensionUris(Set<String> serviceExtensionUris) {
    setPropertyChecked(SERVICE_EXTENSIONS_KEY, checkNotNull(serviceExtensionUris));
  }

  public int getFailedLoginAttempts() {
    return ((Integer) Optional.fromNullable(getPropertyChecked(FAILED_LOGIN_ATTEMPTS_KEY)).or(0));
  }

  public void incrementFailedLoginAttempts() {
    setPropertyChecked(FAILED_LOGIN_ATTEMPTS_KEY, getFailedLoginAttempts() + 1);
  }

  public void resetFailedLoginAttempts() {
    setPropertyChecked(FAILED_LOGIN_ATTEMPTS_KEY, null);
  }

  // These three methods are here to allow special permissions if a derived class overrides them.

  public boolean isDryRun() {
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(getClass())
        .add("system hash code", System.identityHashCode(this))
        .add("clientId", getClientId())
        .add("isSuperuser", isSuperuser())
        .add("failedLoginAttempts", getFailedLoginAttempts())
        .add("sessionSource", getSessionSource())
        .add("serviceExtensionUris", Joiner.on('.').join(nullToEmpty(getServiceExtensionUris())))
        .add("transportCredentials", getTransportCredentials())
        .toString();
  }
}
