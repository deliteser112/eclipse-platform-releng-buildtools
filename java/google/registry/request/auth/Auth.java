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

package google.registry.request.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotation used to configure authentication settings for Actions. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Auth {

  /** Available methods for authentication. */
  public enum AuthMethod {

    /** App Engine internal authentication. Must always be provided as the first method. */
    INTERNAL,

    /** Authentication methods suitable for API-style access, such as OAuth 2. */
    API,

    /** Legacy authentication using cookie-based App Engine Users API. Must come last if present. */
    LEGACY
  }

  /** User authorization policy options. */
  public enum UserPolicy {

    /** This action ignores end users; the only configured auth method must be INTERNAL. */
    IGNORED,

    /** No user policy is enforced; anyone can access this action. */
    PUBLIC,

    /**
     * If there is a user, it must be an admin, as determined by isUserAdmin().
     *
     * <p>Note that, according to App Engine, anybody with access to the app in the GCP Console,
     * including editors and viewers, is an admin.
     */
    ADMIN
  }

  /** Enabled authentication methods for this action. */
  AuthMethod[] methods() default { AuthMethod.INTERNAL };

  /** Required minimum level of authentication for this action. */
  AuthLevel minimumLevel() default AuthLevel.APP;

  /** Required user authorization policy for this action. */
  UserPolicy userPolicy() default UserPolicy.IGNORED;
}
