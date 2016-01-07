// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

goog.provide('registry.registrar.Payment');

goog.require('goog.Uri');
goog.require('goog.asserts');
goog.require('goog.dom');
goog.require('goog.dom.TagName');
goog.require('goog.events.EventType');
goog.require('goog.json');
goog.require('goog.object');
goog.require('goog.soy');
goog.require('registry.Component');
goog.require('registry.MenuButton');
goog.require('registry.Session');
goog.require('registry.forms');
goog.require('registry.registrar.BrainFrame');
goog.require('registry.soy.registrar.console');
goog.require('registry.soy.registrar.payment');
goog.require('registry.util');

goog.forwardDeclare('goog.events.BrowserEvent');
goog.forwardDeclare('registry.registrar.Console');



/**
 * Page allowing registrar to send money to registry.
 *
 * <p>This page contains a form that asks the user to enter an arbitrary amount
 * and a payment method, which can be credit card or PayPal. Multiple
 * currencies are supported.
 *
 * <h3>PCI Compliance</h3>
 *
 * <p>We don't have any access whatsoever to the credit card information. We
 * embed an iframe run by Braintree Payments. The user can then provide his
 * credit card (or PayPal) details directly to Braintree. Braintree then gives
 * us a nonce value representing the payment method, which we can use to issue
 * the transaction.
 *
 * <h3>Bidirectional Protection</h3>
 *
 * <p>To use Braintree's iframe, we need to load a script from their server. We
 * don't want that script to have access to the Registrar Console. If Braintree
 * got pwnd, the attacker would be able to issue EPP commands as a registrar.
 *
 * <p>We fix this problem by embedding the Braintree iframe inside another
 * sandbox iframe that's hosted from a Cloud Storage bucket. This frame is
 * defined by {@code brainframe.html}. It's basically an empty shell that sends
 * a request back to the production environment for {@code brainframe.js}.
 *
 * <p>The importance of the Cloud Storage bucket is that it is served from a
 * separate domain. This causes the browser to forbid the iframe from accessing
 * the contents of the parent frame. The HTML5 {@code sandbox} attribute does
 * this too, but we can't use it, because the Venmo functionality in the
 * Braintree JS SDK needs to be able to access {@code document.cookie}, which
 * is forbidden in a sandbox environment. This HTML5 {@code sandbox} feature is
 * also not available in older versions of Internet Explorer.
 *
 * <h3>Business Logic</h3>
 *
 * <p>This page starts off as a loading glyph, while we issue an RPC to the
 * backend. We ask for a Braintree token, which currencies are available, and
 * the location of the brainframe HTML file. Once we get that data, we render
 * the form.
 *
 * <p>Once the sandbox iframe inside that form has loaded, it'll send us a
 * message asking for the token. We give it the token, which it uses to load
 * the Braintree iframe.
 *
 * <p>To make sure the sandbox iframe is the same size as the Braintree iframe,
 * the sandbox iframe will send us messages on occasion asking to be resized.
 *
 * <p>The disabled state of the submit button is managed judiciously. It's
 * disabled initially, until we get a READY message from the sandbox iframe,
 * indicating that the Braintree iframe is fully loaded. We also disable the
 * submit button during the submit process.
 *
 * <p>When the user presses the submit button, we send a message to the sandbox
 * iframe asking it to submit the Braintree iframe. When the Braintree iframe
 * is submitted, it gives the sandbox iframe the the payment method nonce,
 * which it passes along to us. Then we pass the form data to the backend via
 * the payment RPC, which invokes the Braintree Java API to issue the
 * transaction.
 *
 * <p>If the payment RPC fails, we'll either show an error on a field, or in a
 * bloody butterbar. If it succeeds, then the backend will give us the
 * transaction ID assigned by Braintree, which we then render on a success
 * page.
 *
 * <p>The success page contains a "Make Another Payment" button which, if
 * clicked, will reset the state of this page back to the beginning.
 *
 * @param {!registry.registrar.Console} console
 * @param {string} xsrfToken Security token to pass back to the server.
 * @constructor
 * @extends {registry.Component}
 * @final
 */
registry.registrar.Payment = function(console, xsrfToken) {
  registry.registrar.Payment.base(this, 'constructor', console);

  /**
   * Element in which this page is rendered.
   * @private {!Element}
   * @const
   */
  this.content_ = goog.dom.getRequiredElement('reg-content');

  /**
   * Braintree API nonce token generated by the backend. This value is a
   * prerequisite to rendering the Braintree iframe.
   * @private {string}
   */
  this.token_ = '';

  /**
   * Braintree API nonce value for payment method selected by user.
   * @private {string}
   */
  this.paymentMethodNonce_ = '';

  /**
   * Currency drop-down widget in form.
   * @private {?registry.MenuButton}
   */
  this.currencyMenu_ = null;

  /**
   * XHR client to {@code RegistrarPaymentSetupAction}.
   * @private {!registry.Session.<!registry.rpc.PaymentSetup.Request,
   *                                 !registry.rpc.PaymentSetup.Response>}
   * @const
   */
  this.setupRpc_ =
      new registry.Session(new goog.Uri('/registrar-payment-setup'),
                           xsrfToken,
                           registry.Session.ContentType.JSON);

  /**
   * XHR client to {@code RegistrarPaymentAction}.
   * @private {!registry.Session.<!registry.rpc.Payment.Request,
   *                                 !registry.rpc.Payment.Response>}
   * @const
   */
  this.paymentRpc_ =
      new registry.Session(new goog.Uri('/registrar-payment'),
                           xsrfToken,
                           registry.Session.ContentType.JSON);

  this.listen(goog.global.window,
              goog.events.EventType.MESSAGE,
              this.onMessage_);
};
goog.inherits(registry.registrar.Payment, registry.Component);


/** @override */
registry.registrar.Payment.prototype.bindToDom = function(id) {
  registry.registrar.Payment.base(this, 'bindToDom', id);
  if (!goog.isNull(goog.dom.getElement('reg-app-buttons'))) {
    goog.dom.removeChildren(goog.dom.getElement('reg-app-buttons'));
  }
  if (!registry.registrar.Payment.isBrowserSupported_()) {
    goog.soy.renderElement(this.content_,
                           registry.soy.registrar.payment.unsupported);
    return;
  }
  goog.soy.renderElement(this.content_, registry.soy.registrar.console.loading);
  this.setupRpc_.sendXhrIo({}, goog.bind(this.onSetup_, this));
};


/**
 * Handler invoked when we receive information from our backend, such as a
 * Braintree token, which is necessary for us to render the payment form.
 * @param {!registry.rpc.PaymentSetup.Response} response
 * @private
 */
registry.registrar.Payment.prototype.onSetup_ = function(response) {
  if (response.status != 'SUCCESS') {
    if (response.message == 'not-using-cc-billing') {
      goog.soy.renderElement(this.content_,
                             registry.soy.registrar.payment.notUsingCcBilling);
    } else {
      registry.forms.displayError(response.message);
    }
    return;
  }
  var result = response.results[0];
  this.token_ = result.token;
  this.paymentMethodNonce_ = '';
  goog.soy.renderElement(this.content_,
                         registry.soy.registrar.payment.form,
                         result);
  this.listen(
      goog.dom.getRequiredElementByClass(goog.getCssName('reg-payment-form')),
      goog.events.EventType.SUBMIT,
      this.onSubmit_);
  this.currencyMenu_ =
      new registry.MenuButton(goog.dom.getRequiredElement('currency'));
  this.registerDisposable(this.currencyMenu_);
};


/**
 * Handler invoked when payment form is submitted.
 * @param {!goog.events.BrowserEvent} e
 * @private
 */
registry.registrar.Payment.prototype.onSubmit_ = function(e) {
  e.preventDefault();
  this.submit_();
};


/**
 * Submits payment form.
 * @private
 */
registry.registrar.Payment.prototype.submit_ = function() {
  registry.forms.resetErrors();
  registry.registrar.Payment.setEnabled_(false);
  if (this.paymentMethodNonce_ == '') {
    this.send_(
        'type', registry.registrar.BrainFrame.MessageType.SUBMIT_REQUEST);
    return;
  }
  this.paymentRpc_.sendXhrIo(
      {
        amount: goog.dom.getRequiredElement('amount').value,
        currency: this.currencyMenu_.getValue(),
        paymentMethodNonce: this.paymentMethodNonce_
      },
      goog.bind(this.onPayment_, this));
};


/**
 * Callback for backend payment RPC that issues the transaction.
 * @param {!registry.rpc.Payment.Response} response
 * @private
 */
registry.registrar.Payment.prototype.onPayment_ = function(response) {
  registry.registrar.Payment.setEnabled_(true);
  if (response.status != 'SUCCESS') {
    registry.forms.displayError(response.message, response.field);
    return;
  }
  goog.soy.renderElement(this.content_,
                         registry.soy.registrar.payment.success,
                         response.results[0]);
  this.listenOnce(
      goog.dom.getRequiredElementByClass(goog.getCssName('reg-payment-again')),
      goog.events.EventType.CLICK,
      this.bindToDom);
};


/**
 * Handler invoked when {@code brainframe.js} sends us a message.
 * @param {!goog.events.BrowserEvent} e
 * @private
 */
registry.registrar.Payment.prototype.onMessage_ = function(e) {
  var msg = /** @type {!MessageEvent.<string>} */ (e.getBrowserEvent());
  var brainframe =
      goog.dom.getElementByClass(goog.getCssName('reg-payment-form-method'));
  if (brainframe == null ||
      msg.source != goog.dom.getFrameContentWindow(brainframe)) {
    return;
  }
  var data;
  try {
    data = goog.json.parse(msg.data);
  } catch (ex) {
    // TODO(b/26876003): Figure out why it's possible that the Braintree iframe
    //                   is able to propagate messages up to our level.
    registry.util.log(ex, msg.source, msg.data);
    return;
  }
  switch (goog.object.get(data, 'type')) {
    case registry.registrar.BrainFrame.MessageType.TOKEN_REQUEST:
      goog.asserts.assert(this.token_ != '');
      this.send_(
          'type', registry.registrar.BrainFrame.MessageType.TOKEN_RESPONSE,
          'token', this.token_);
      break;
    case registry.registrar.BrainFrame.MessageType.RESIZE_REQUEST:
      brainframe.height = goog.object.get(data, 'height');
      break;
    case registry.registrar.BrainFrame.MessageType.READY:
      registry.registrar.Payment.setEnabled_(true);
      break;
    case registry.registrar.BrainFrame.MessageType.SUBMIT_ERROR:
      registry.registrar.Payment.setEnabled_(true);
      registry.forms.displayError(goog.object.get(data, 'message'), 'method');
      break;
    case registry.registrar.BrainFrame.MessageType.PAYMENT_METHOD:
      registry.registrar.Payment.setEnabled_(true);
      this.setPaymentMethod_(
          /** @type {!braintreepayments.PaymentMethod} */ (
              goog.object.get(data, 'method')));
      if (goog.object.get(data, 'submit')) {
        this.submit_();
      }
      break;
    default:
      throw Error('Unexpected message: ' + msg.data);
  }
};


/**
 * Updates UI to display selected payment method.
 *
 * <p>We remove the iframe from the page as soon as this happens, because the
 * UI would be busted otherwise. The Braintree UI for changing the payment
 * method (after it's been entered) does not appear to stop respond to submit
 * events. It also causes ugly scroll bars to appear inside the iframe.
 *
 * <p>This approach is also advantageous for screenshot testing. We do not want
 * our continuous integration testing system to talk to Braintree's servers. So
 * we mock out the brainframe with {@code integration-test-brainframe.html}
 * which <i>only</i> sends us a METHOD message, which we then render ourselves.
 *
 * @param {!braintreepayments.PaymentMethod} pm
 * @private
 */
registry.registrar.Payment.prototype.setPaymentMethod_ = function(pm) {
  registry.forms.resetErrors();
  goog.dom.removeNode(
      goog.dom.getElementByClass(goog.getCssName('reg-payment-form-method')));
  var paymentMethodInfoBox =
      goog.dom.getRequiredElementByClass(
          goog.getCssName('reg-payment-form-method-info'));
  switch (pm.type) {
    case 'CreditCard':
      goog.soy.renderElement(
          paymentMethodInfoBox,
          registry.soy.registrar.payment.methodInfoCard,
          {cardType: pm.details.cardType, lastTwo: pm.details.lastTwo});
      break;
    case 'PayPalAccount':
      goog.soy.renderElement(
          paymentMethodInfoBox,
          registry.soy.registrar.payment.methodInfoPaypal,
          {email: pm.details.email});
      break;
    default:
      throw Error('Unknown payment method: ' + pm.type);
  }
  registry.util.setVisible(paymentMethodInfoBox, true);
  this.paymentMethodNonce_ = pm.nonce;
};


/**
 * Sends message to brainframe.
 * @param {...*} var_args Passed along to {@code goog.object.create}.
 * @private
 */
registry.registrar.Payment.prototype.send_ = function(var_args) {
  goog.asserts.assert(arguments[0] == 'type');
  var brainframeWindow =
      goog.dom.getFrameContentWindow(
          goog.dom.getRequiredElementByClass(
              goog.getCssName('reg-payment-form-method')));
  // We send a string value to support older versions of IE.
  brainframeWindow.postMessage(
      goog.json.serialize(goog.object.create.apply(null, arguments)),
      '*');
};


/**
 * Enables submit button and hides mini loading glyph.
 * @param {boolean} enabled
 * @private
 */
registry.registrar.Payment.setEnabled_ = function(enabled) {
  registry.forms.setEnabled(
      goog.dom.getRequiredElementByClass(
          goog.getCssName('reg-payment-form-submit')), enabled);
  registry.util.setVisible(
      goog.dom.getRequiredElementByClass(
          goog.getCssName('reg-payment-form-loader')), !enabled);
};


/**
 * Returns {@code true} if browser has all the features we need.
 * @return {boolean}
 * @private
 * @see "http://caniuse.com/#feat=dispatchevent"
 */
registry.registrar.Payment.isBrowserSupported_ = function() {
  // dispatchEvent is used by brainframe.js and is IE 9+.
  return goog.object.containsKey(
      goog.dom.createElement(goog.dom.TagName.FORM),
      'dispatchEvent');
};
