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
 * @fileoverview Braintree JS SDK v2 externs. This file tells the Closure
 * Compiler how Braintree's API is defined, which allows us to use it with type
 * safety and dot-notation.
 * @externs
 */


/**
 * @type {!braintreepayments.Braintree}
 */
var braintree;


/**
 * Fake namespace for pure Closure Compiler types not defined by the SDK.
 */
var braintreepayments = {};



/**
 * @constructor
 * @final
 */
braintreepayments.Braintree = function() {};


/**
 * @param {string} clientTokenFromServer
 * @param {string} integrationType Either 'dropin' or 'custom'.
 * @param {{container: (string|!Element|undefined),
 *          dataCollector: (!Object|undefined),
 *          enableCORS: (boolean|undefined),
 *          form: (string|undefined),
 *          hostedFields: (!Object|undefined),
 *          id: (string|undefined),
 *          onError: (function(!braintreepayments.Error)|undefined),
 *          onPaymentMethodReceived:
 *              (function(!braintreepayments.PaymentMethod)|undefined),
 *          onReady: (function(!braintreepayments.Integrator)|undefined),
 *          paypal: (undefined|{
 *            amount: (number|undefined),
 *            container: (string|!Element),
 *            currency: (string|undefined),
 *            displayName: (string|undefined),
 *            enableBillingAddress: (boolean|undefined),
 *            enableShippingAddress: (boolean|undefined),
 *            headless: (boolean|undefined),
 *            locale: (string|undefined),
 *            onCancelled: (function()|undefined),
 *            onPaymentMethodReceived:
 *                (function(!braintreepayments.PaymentMethod)|undefined),
 *            onUnsupported: (function()|undefined),
 *            paymentMethodNonceInputField: (string|!Element|undefined),
 *            shippingAddressOverride: (undefined|{
 *              recipientName: string,
 *              streetAddress: string,
 *              extendedAddress: (string|undefined),
 *              locality: string,
 *              countryCodeAlpha2: string,
 *              postalCode: string,
 *              region: string,
 *              phone: (string|undefined),
 *              editable: boolean
 *            }),
 *            singleUse: (boolean|undefined)
 *          })
 *        }} options
 * @see https://developers.braintreepayments.com/guides/client-sdk/javascript/v2#global-setup
 */
braintreepayments.Braintree.prototype.setup =
    function(clientTokenFromServer, integrationType, options) {};



/**
 * @constructor
 * @final
 * @see https://developers.braintreepayments.com/guides/drop-in/javascript/v2#onerror
 */
braintreepayments.Error = function() {};


/**
 * Describes type of error that occurred (e.g. "CONFIGURATION", "VALIDATION".)
 * @type {string}
 * @const
 */
braintreepayments.Error.prototype.type;


/**
 * Human-readable string describing the error.
 * @type {string}
 * @const
 */
braintreepayments.Error.prototype.message;


/**
 * @type {(!braintreepayments.ErrorDetails|undefined)}
 * @const
 */
braintreepayments.Error.prototype.details;



/**
 * @constructor
 * @final
 */
braintreepayments.ErrorDetails = function() {};


/**
 * @type {(!Array<!braintreepayments.ErrorField>|undefined)}
 * @const
 */
braintreepayments.ErrorDetails.prototype.invalidFields;



/**
 * @constructor
 * @final
 */
braintreepayments.ErrorField = function() {};


/**
 * Field which failed validation. It will match one of the following: "number",
 * "cvv", "expiration", or "postalCode".
 * @type {string}
 * @const
 */
braintreepayments.ErrorField.prototype.fieldKey;


/**
 * This will be {@code true} if the associated input is empty.
 * @type {(boolean|undefined)}
 * @const
 */
braintreepayments.ErrorField.prototype.isEmpty;



/**
 * @constructor
 * @final
 */
braintreepayments.Integrator = function() {};


/**
 * @type {string}
 * @const
 */
braintreepayments.Integrator.prototype.deviceData;


/**
 * @type {!braintreepayments.PaypalIntegrator}
 * @const
 */
braintreepayments.Integrator.prototype.paypal;


/**
 * @param {function()=} opt_callback
 * @see https://developers.braintreepayments.com/guides/client-sdk/javascript/v2#teardown
 */
braintreepayments.Integrator.prototype.teardown = function(opt_callback) {};



/**
 * @constructor
 * @final
 */
braintreepayments.PaypalIntegrator = function() {};


braintreepayments.PaypalIntegrator.prototype.closeAuthFlow = function() {};


braintreepayments.PaypalIntegrator.prototype.initAuthFlow = function() {};



/**
 * @constructor
 * @final
 */
braintreepayments.PaymentMethod = function() {};


/**
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethod.prototype.nonce;


/**
 * Either 'CreditCard' or 'PayPalAccount'.
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethod.prototype.type;


/**
 * @type {(!braintreepayments.PaymentMethodDetailsCard|
 *         !braintreepayments.PaymentMethodDetailsPaypal)}
 * @const
 */
braintreepayments.PaymentMethod.prototype.details;



/**
 * @constructor
 * @final
 * @see https://developers.braintreepayments.com/guides/client-sdk/javascript/v2#payment-method-details
 */
braintreepayments.PaymentMethodDetailsCard = function() {};


/**
 * Can be 'Visa', 'MasterCard', 'Discover', 'Amex', or 'JCB'.
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethodDetailsCard.prototype.cardType;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethodDetailsCard.prototype.lastTwo;



/**
 * @constructor
 * @final
 * @see https://developers.braintreepayments.com/guides/paypal/client-side/javascript/v2#options
 */
braintreepayments.PaymentMethodDetailsPaypal = function() {};


/**
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethodDetailsPaypal.prototype.email;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethodDetailsPaypal.prototype.firstName;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethodDetailsPaypal.prototype.lastName;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethodDetailsPaypal.prototype.phone;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaymentMethodDetailsPaypal.prototype.payerID;


/**
 * @type {!braintreepayments.PaypalShippingAddress}
 * @const
 */
braintreepayments.PaymentMethodDetailsPaypal.prototype.shippingAddress;


/**
 * @type {(!braintreepayments.PaypalBillingAddress|undefined)}
 * @const
 */
braintreepayments.PaymentMethodDetailsPaypal.prototype.billingAddress;



/**
 * @constructor
 * @final
 */
braintreepayments.PaypalShippingAddress = function() {};


/**
 * @type {number}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.id;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.type;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.recipientName;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.streetAddress;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.extendedAddress;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.locality;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.region;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.postalCode;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.countryCodeAlpha;


/**
 * @type {boolean}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.defaultAddress;


/**
 * @type {boolean}
 * @const
 */
braintreepayments.PaypalShippingAddress.prototype.preferredAddress;



/**
 * @constructor
 * @final
 */
braintreepayments.PaypalBillingAddress = function() {};


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalBillingAddress.prototype.streetAddress;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalBillingAddress.prototype.extendedAddress;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalBillingAddress.prototype.locality;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalBillingAddress.prototype.region;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalBillingAddress.prototype.postalCode;


/**
 * @type {string}
 * @const
 */
braintreepayments.PaypalBillingAddress.prototype.countryCodeAlpha2;
