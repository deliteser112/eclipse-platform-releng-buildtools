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

package com.google.domain.registry.braintree;

import com.google.domain.registry.config.ConfigModule.Config;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.keyring.api.KeyModule.Key;

import com.braintreegateway.BraintreeGateway;

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

/** Dagger module for Braintree Payments API. */
@Module
public final class BraintreeModule {

   @Provides
   @Singleton
   static BraintreeGateway provideBraintreeGateway(
       RegistryEnvironment environment,
       @Config("braintreeMerchantId") String merchantId,
       @Config("braintreePublicKey") String publicKey,
       @Key("braintreePrivateKey") String privateKey) {
     return new BraintreeGateway(
         environment == RegistryEnvironment.PRODUCTION
             ? com.braintreegateway.Environment.PRODUCTION
             : com.braintreegateway.Environment.SANDBOX,
         merchantId,
         publicKey,
         privateKey);
   }
}
