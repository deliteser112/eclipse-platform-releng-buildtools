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

/**
 * @fileoverview RegistrarPaymentAction JSON RPC definitions.
 * @externs
 */


/**
 * @suppress {duplicate}
 */
var registry = {};


/**
 * @suppress {duplicate}
 */
registry.rpc = {};
registry.rpc.Payment = {};


/**
 * @typedef {{
 *   currency: string,
 *   amount: string,
 *   paymentMethodNonce: string
 * }}
 */
registry.rpc.Payment.Request;


/**
 * @typedef {registry.json.Response.<!registry.rpc.Payment.Result>}
 */
registry.rpc.Payment.Response;



/**
 * @constructor
 */
registry.rpc.Payment.Result = function() {};


/**
 * @type {string}
 */
registry.rpc.Payment.Result.prototype.id;


/**
 * @type {string}
 */
registry.rpc.Payment.Result.prototype.formattedAmount;
