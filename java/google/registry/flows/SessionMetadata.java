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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.util.CollectionUtils.nullToEmpty;

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

  /** Check that the session is valid and set a property. */
  private void setPropertyChecked(String key, Object value) {
    checkValid();
    setProperty(key, value);
  }

  /**
   * Check that the session is valid and get a property as a given type.
   *
   * @param clazz type to return, specified as a param to enforce typesafe generics
   * @see "http://errorprone.info/bugpattern/TypeParameterUnusedInFormals"
   */
  private <T> T getProperty(Class<T> clazz, String key) {
    checkValid();
    return clazz.cast(getProperty(key));
  }

  public String getClientId() {
    return getProperty(String.class, CLIENT_ID_KEY);
  }

  public boolean isSuperuser() {
    return Boolean.TRUE.equals(getProperty(Boolean.class, SUPERUSER_KEY));
  }

  @SuppressWarnings("unchecked")
  public Set<String> getServiceExtensionUris() {
    return getProperty(Set.class, SERVICE_EXTENSIONS_KEY);
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
    return Optional.fromNullable(getProperty(Integer.class, FAILED_LOGIN_ATTEMPTS_KEY))
        .or(0);
  }

  public void incrementFailedLoginAttempts() {
    setPropertyChecked(FAILED_LOGIN_ATTEMPTS_KEY, getFailedLoginAttempts() + 1);
  }

  public void resetFailedLoginAttempts() {
    setPropertyChecked(FAILED_LOGIN_ATTEMPTS_KEY, null);
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
        .toString();
  }
}
