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

goog.provide('registry.registrar.BrainFrame');
goog.provide('registry.registrar.BrainFrame.main');

goog.require('goog.Timer');
goog.require('goog.asserts');
goog.require('goog.dom');
goog.require('goog.dom.TagName');
goog.require('goog.events.EventHandler');
goog.require('goog.events.EventType');
goog.require('goog.json');
goog.require('goog.object');
goog.require('goog.style');

goog.forwardDeclare('goog.events.BrowserEvent');



/**
 * Sandboxed iframe for Braintree JS SDK v2 iframe.
 *
 * <p>This class adds an additional layer of security between the Registrar
 * Console and JavaScript loaded from Braintree's web server.
 *
 * <p>The main function for this class is compiled into a separate binary,
 * which is loaded within an iframe that's hosted on a different domain than
 * the production environment. This ensures that cross origin browser security
 * policies take effect.
 *
 * @param {string} origin FQDN of production environment.
 * @param {string} containerId ID of Braintree container element.
 * @constructor
 * @extends {goog.events.EventHandler}
 * @final
 */
registry.registrar.BrainFrame = function(origin, containerId) {
  registry.registrar.BrainFrame.base(this, 'constructor');

  /**
   * Hostname of production registry, e.g. domain-registry.appspot.com.
   * @private {string}
   * @const
   */
  this.origin_ = origin;

  /**
   * Div that wraps Braintree iframe.
   * @private {!Element}
   * @const
   */
  this.container_ = goog.dom.getRequiredElement(containerId);

  /**
   * Last known height of {@code container_}.
   * @private {number}
   */
  this.containerHeight_ = 0;

  /**
   * Timer polling for changes in Braintree iframe height.
   * @private {!goog.Timer}
   * @const
   */
  this.resizeTimer_ = new goog.Timer(1000 / 30);
  this.registerDisposable(this.resizeTimer_);
  this.listen(this.resizeTimer_, goog.Timer.TICK, this.onResizeTimer_);

  /**
   * Form that wraps {@code container_}.
   * @private {?Element}
   * @const
   */
  this.form_ = goog.dom.getAncestorByTagNameAndClass(this.container_,
                                                     goog.dom.TagName.FORM);
  goog.asserts.assert(this.form_ != null);

  /**
   * State indicating if we're submitting at behest of parent.
   * @private {boolean}
   */
  this.isSubmitting_ = false;

  this.listen(goog.global.window,
              goog.events.EventType.MESSAGE,
              this.onMessage_);
};
goog.inherits(registry.registrar.BrainFrame, goog.events.EventHandler);


/**
 * Runs Braintree sandbox environment.
 */
registry.registrar.BrainFrame.prototype.run = function() {
  this.send_(
      'type', registry.registrar.BrainFrame.MessageType.TOKEN_REQUEST);
};


/**
 * Handles message from parent iframe which sends Braintree token.
 * @param {!goog.events.BrowserEvent} e
 * @private
 */
registry.registrar.BrainFrame.prototype.onMessage_ = function(e) {
  var msg = /** @type {!MessageEvent.<string>} */ (e.getBrowserEvent());
  if (msg.source != goog.global.window.parent) {
    return;
  }
  if (this.origin_ != '*' && this.origin_ != msg.origin) {
    throw new Error(
        'Message origin is "' + msg.origin + '" but wanted: ' + this.origin_);
  }
  var data = goog.json.parse(msg.data);
  switch (goog.object.get(data, 'type')) {
    case registry.registrar.BrainFrame.MessageType.TOKEN_RESPONSE:
      goog.global.braintree.setup(goog.object.get(data, 'token'), 'dropin', {
        container: this.container_,
        onPaymentMethodReceived: goog.bind(this.onPaymentMethod_, this),
        onReady: goog.bind(this.onReady_, this),
        onError: goog.bind(this.onError_, this)
      });
      this.resizeTimer_.start();
      break;
    case registry.registrar.BrainFrame.MessageType.SUBMIT_REQUEST:
      this.isSubmitting_ = true;
      // Trigger Braintree JS SDK submit event listener. It does not appear to
      // be possible to do this using the Closure Library. This is IE 9+ only.
      this.form_.dispatchEvent(new Event(goog.events.EventType.SUBMIT));
      break;
    default:
      throw Error('Unexpected message: ' + msg.data);
  }
};


/**
 * Polls for resizes of Braintree iframe and propagates them to the parent
 * frame which will then use it to resize this iframe.
 * @private
 */
registry.registrar.BrainFrame.prototype.onResizeTimer_ = function() {
  var height = goog.style.getSize(this.container_).height;
  if (height != this.containerHeight_) {
    this.send_(
        'type', registry.registrar.BrainFrame.MessageType.RESIZE_REQUEST,
        'height', height);
    this.containerHeight_ = height;
  }
};


/**
 * Callback Braintree iframe has fully loaded.
 * @private
 */
registry.registrar.BrainFrame.prototype.onReady_ = function() {
  this.send_('type', registry.registrar.BrainFrame.MessageType.READY);
};


/**
 * Callback Braintree says an error happened.
 * @param {!braintreepayments.Error} error
 * @private
 */
registry.registrar.BrainFrame.prototype.onError_ = function(error) {
  this.isSubmitting_ = false;
  this.send_('type', registry.registrar.BrainFrame.MessageType.SUBMIT_ERROR,
             'message', error.message);
};


/**
 * Callback when user successfully gave Braintree payment details.
 * @param {!braintreepayments.PaymentMethod} pm
 * @private
 */
registry.registrar.BrainFrame.prototype.onPaymentMethod_ = function(pm) {
  // TODO(b/26829319): The Braintree JS SDK does not seem to recognize the
  //                   enter key while embedded inside our sandbox iframe. So
  //                   at this time, this callback will only be invoked after
  //                   we've submitted the form manually at the behest of
  //                   payment.js, which means isSubmitting_ will be true.
  this.send_(
      'type', registry.registrar.BrainFrame.MessageType.PAYMENT_METHOD,
      'submit', this.isSubmitting_,
      'method', pm);
  this.isSubmitting_ = false;
};


/**
 * Sends message to parent iframe.
 * @param {...*} var_args Passed along to {@code goog.object.create}.
 * @private
 */
registry.registrar.BrainFrame.prototype.send_ = function(var_args) {
  goog.asserts.assert(arguments[0] == 'type');
  registry.registrar.BrainFrame.postMessage_(
      goog.json.serialize(goog.object.create.apply(null, arguments)),
      this.origin_);
};


/**
 * Delegates to {@code window.parent.postMessage}. This method exists because
 * IE will not allow us to mock methods on the window object.
 * @param {string} message
 * @param {string} origin
 * @private
 */
registry.registrar.BrainFrame.postMessage_ = function(message, origin) {
  goog.global.window.parent.postMessage(message, origin);
};


/**
 * Message types passed between brainframe and payment page.
 * @enum {string}
 */
registry.registrar.BrainFrame.MessageType = {

  /** Brainframe asks payment page for Braintree token. */
  TOKEN_REQUEST: 'token_request',

  /** Payment page sends brainframe Braintree token. */
  TOKEN_RESPONSE: 'token_response',

  /** Brainframe asks payment page to be resized. */
  RESIZE_REQUEST: 'resize_request',

  /** Brainframe tells payment page it finished loading. */
  READY: 'ready',

  /** Payment page asks brainframe to submit Braintree payment method form. */
  SUBMIT_REQUEST: 'submit_request',

  /** Brainframe tells payment page it failed to submit. */
  SUBMIT_ERROR: 'submit_error',

  /** Brainframe gives payment method info and nonce to payment page. */
  PAYMENT_METHOD: 'payment_method'
};


/**
 * Entrypoint for {@link registry.registrar.BrainFrame}.
 * @param {string} origin
 * @param {string} containerId
 * @export
 */
registry.registrar.BrainFrame.main = function(origin, containerId) {
  new registry.registrar.BrainFrame(origin, containerId).run();
};
