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

goog.setTestOnly();

goog.require('goog.dispose');
goog.require('goog.dom');
goog.require('goog.dom.classlist');
goog.require('goog.events.EventType');
goog.require('goog.json');
goog.require('goog.testing.PropertyReplacer');
goog.require('goog.testing.asserts');
goog.require('goog.testing.events');
goog.require('goog.testing.events.Event');
goog.require('goog.testing.jsunit');
goog.require('goog.testing.net.XhrIo');
goog.require('registry.registrar.Payment');
goog.require('registry.testing');


var $ = goog.dom.getRequiredElement;
var $$ = goog.dom.getRequiredElementByClass;
var stubs = new goog.testing.PropertyReplacer();

var page;


function setUp() {
  registry.testing.addToDocument('<div id="reg-content"></div>');
  registry.testing.addToDocument('<div class="kd-butterbar"></div>');
  registry.testing.addToDocument('<div class="kd-butterbar-text"></div>');
  stubs.setPath('goog.net.XhrIo', goog.testing.net.XhrIo);
  page = new registry.registrar.Payment(null, '?');
  page.bindToDom();
}


function tearDown() {
  goog.dispose(page);
  stubs.reset();
  goog.testing.net.XhrIo.cleanup();
}


function testRenderForm() {
  registry.testing.assertReqMockRsp(
      '?',
      '/registrar-payment-setup',
      {},
      {
        status: 'SUCCESS',
        results: [
          {
            token: 'omg-im-a-token',
            currencies: ['LOL', 'OMG'],
            brainframe: ''
          }
        ]
      });
  assertEquals('', $('amount').value);
  assertEquals('LOL', goog.dom.getTextContent($$('selected', $('currency'))));
  assertTrue(
      goog.dom.classlist.contains($$('reg-payment-form-submit'), 'disabled'));
}


function testResize() {
  testRenderForm();
  send({
    type: 'resize_request',
    height: 123
  });
  assertEquals('123', $$('reg-payment-form-method').height);
}


function testReady() {
  testRenderForm();
  send({type: 'ready'});
  assertFalse(
      goog.dom.classlist.contains($$('reg-payment-form-submit'), 'disabled'));
}


function testPaymentMethodCard() {
  testRenderForm();
  send({
    type: 'payment_method',
    method: {
      type: 'CreditCard',
      nonce: 'omg-im-a-nonce',
      details: {
        cardType: 'Amex',
        lastTwo: '12'
      }
    }
  });
  assertEquals(
      'American Express: xxxx xxxxxx xxx12',
      goog.dom.getTextContent($$('reg-payment-form-method-info')));
}


function testPaymentMethodPaypal() {
  testRenderForm();
  send({
    type: 'payment_method',
    method: {
      type: 'PayPalAccount',
      nonce: 'omg-im-a-nonce',
      details: {
        email: 'sparrows@nightingales.example'
      }
    }
  });
  assertEquals(
      'PayPal: sparrows@nightingales.example',
      goog.dom.getTextContent($$('reg-payment-form-method-info')));
}


function testBadAmount_displaysError() {
  testPaymentMethodCard();
  $('amount').value = '3.14';
  submit();
  registry.testing.assertReqMockRsp(
      '?',
      '/registrar-payment',
      {
        amount: '3.14',
        currency: 'LOL',
        paymentMethodNonce: 'omg-im-a-nonce'
      },
      {
        status: 'ERROR',
        message: 'gimmeh moar money',
        field: 'amount'
      });
  assertTrue(goog.dom.classlist.contains($('amount'), 'kd-formerror'));
  assertEquals('gimmeh moar money',
               goog.dom.getTextContent($$('kd-errormessage')));
}


function testGoodPayment_displaysSuccessPage() {
  testPaymentMethodCard();
  $('amount').value = '314';
  submit();
  registry.testing.assertReqMockRsp(
      '?',
      '/registrar-payment',
      {
        amount: '314',
        currency: 'LOL',
        paymentMethodNonce: 'omg-im-a-nonce'
      },
      {
        status: 'SUCCESS',
        results: [
          {
            id: 'omg-im-an-id',
            formattedAmount: '$314'
          }
        ]
      });
  assertContains('Payment Processed',
                 goog.dom.getTextContent($$('reg-payment')));
  assertContains('omg-im-an-id',
                 goog.dom.getTextContent($$('reg-payment')));
  assertContains('$314',
                 goog.dom.getTextContent($$('reg-payment')));
}


/**
 * Sends message to page.
 * @param {string} message
 */
function send(message) {
  page.onMessage_({
    getBrowserEvent: function() {
      return {
        source: goog.dom.getFrameContentWindow($$('reg-payment-form-method')),
        data: goog.json.serialize(message)
      };
    }
  });
}


/** Submits payment form. */
function submit() {
  goog.testing.events.fireBrowserEvent(
      new goog.testing.events.Event(
          goog.events.EventType.SUBMIT,
          $$('reg-payment-form')));
}
