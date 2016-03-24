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

import com.google.domain.registry.flows.EppException.AuthenticationErrorException;
import com.google.domain.registry.model.registrar.Registrar;

/**
 * A marker interface for objects containing registrar credentials provided via an EPP transport.
 */
public interface TransportCredentials {

  /**
   * Indicates whether the transport takes the place of EPP login checks, in which case LoginFlow
   * will not check the password. Alternatively, if the password should be checked, it MUST match
   * the user's and GAE's isUserAdmin should not be used to bypass this check as internal
   * connections over RPC will have this property for all registrars.
   */
  boolean performsLoginCheck();

  /**
   * Called by {@link com.google.domain.registry.flows.session.LoginFlow LoginFlow}
   * to check the transport credentials against the stored registrar's credentials.
   * If they do not match, throw an AuthenticationErrorException.
   */
  void validate(Registrar r) throws AuthenticationErrorException;
}
