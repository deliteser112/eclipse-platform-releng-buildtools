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
goog.require('goog.style');
goog.require('goog.testing.MockControl');
goog.require('goog.testing.asserts');
goog.require('goog.testing.jsunit');
goog.require('registry.registrar.BrainFrame');
goog.require('registry.testing');


var mocks = new goog.testing.MockControl();

var brainframe;
var mockPostMessage;


function setUp() {
  registry.testing.addToDocument('<form><div id="bf"></div></form>');
  brainframe = new registry.registrar.BrainFrame('omg', 'bf');
  mockPostMessage =
      mocks.createMethodMock(registry.registrar.BrainFrame, 'postMessage_');
}


function tearDown() {
  goog.dispose(brainframe);
  mocks.$tearDown();
}


function testRun_sendsTokenRequestToParent() {
  mockPostMessage('{"type":"token_request"}', 'omg');
  mocks.$replayAll();
  brainframe.run();
  mocks.$verifyAll();
}


function testTokenResponseMessage_callsSetup() {
  var called = false;
  goog.global.braintree = {};
  goog.global.braintree.setup = function(token, mode, args) {
    called = true;
    assertEquals('imatoken', token);
    assertEquals('dropin', mode);
    assertEquals('bf', args.container.id);
  };
  brainframe.onMessage_({
    getBrowserEvent: function() {
      return {
        source: window.parent,
        origin: 'omg',
        data: '{"type": "token_response", "token": "imatoken"}'
      };
    }
  });
  assertTrue(called);
}


function testPaymentMethodMessage_sendsInfo() {
  mockPostMessage('{"type":"payment_method","submit":false,"method":"hi"}',
                  'omg');
  mocks.$replayAll();
  brainframe.onPaymentMethod_('hi');
  mocks.$verifyAll();
}


function testOnResizeTimer_sendsHeight() {
  mockPostMessage('{"type":"resize_request","height":123}', 'omg');
  mocks.$replayAll();
  goog.style.setHeight(goog.dom.getElement('bf'), 123);
  brainframe.onResizeTimer_();
  mocks.$verifyAll();

  // But does not send height if size hasn't changed.
  mocks.$replayAll();
  brainframe.onResizeTimer_();
  mocks.$verifyAll();
}
