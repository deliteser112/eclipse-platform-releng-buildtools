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

goog.require('goog.dom');
goog.require('goog.dom.classlist');
goog.require('goog.json');
goog.require('goog.soy');
goog.require('goog.testing.MockControl');
goog.require('goog.testing.PropertyReplacer');
goog.require('goog.testing.asserts');
goog.require('goog.testing.jsunit');
goog.require('goog.testing.mockmatchers');
goog.require('goog.testing.net.XhrIo');
goog.require('registry.registrar.ConsoleTestUtil');
goog.require('registry.soy.registrar.console');
goog.require('registry.testing');
goog.require('registry.util');


var $ = goog.dom.getRequiredElement;
var stubs = new goog.testing.PropertyReplacer();

var test = {
  testXsrfToken: 'testToken',
  testClientId: 'daddy',
  mockControl: new goog.testing.MockControl()
};


function setUp() {
  registry.testing.addToDocument('<div id="test"/>');
  registry.testing.addToDocument('<div class="kd-butterbar"/>');
  stubs.setPath('goog.net.XhrIo', goog.testing.net.XhrIo);
  var testElt = goog.dom.getElement('test');
  goog.soy.renderElement(testElt, registry.soy.registrar.console.main, {
    xsrfToken: test.testXsrfToken,
    username: 'blah',
    logoutUrl: 'omg',
    isAdmin: true,
    clientId: test.testClientId,
    showPaymentLink: false,
    logoFilename: 'logo.png',
    productName: 'Nomulus',
    integrationEmail: 'integration@example.com',
    supportEmail: 'support@example.com',
    announcementsEmail: 'announcement@example.com',
    supportPhoneNumber: '+1 (888) 555 0123',
    technicalDocsUrl: 'http://example.com/techdocs',
  });
  registry.registrar.ConsoleTestUtil.setup(test);
  var regNavlist = $('reg-navlist');
  var active = regNavlist.querySelector('a[href="/registrar#contact-us"]');
  assertTrue(active != null);
}


function tearDown() {
  goog.testing.net.XhrIo.cleanup();
  stubs.reset();
  test.mockControl.$tearDown();
}


function testButter() {
  registry.registrar.ConsoleTestUtil.visit(test, {
    productName: 'Foo Registry'
  });
  registry.util.butter('butter msg');
  var butter = goog.dom.getElementByClass(goog.getCssName('kd-butterbar'));
  assertNotNull(butter.innerHTML.match(/.*butter msg.*/));
  assertTrue(goog.dom.classlist.contains(butter, goog.getCssName('shown')));
}


/**
 * The EPP login should be triggered if the user {@code isGaeLoggedIn}
 * but not yet {@code isEppLoggedIn}.
 */
function testEppLogin() {
  // This is a little complex, as handleHashChange triggers an async
  // event to do the EPP login with a callback to come back to
  // handleHashChange after completion.
  registry.registrar.ConsoleTestUtil.visit(
      test, {
        isEppLoggedIn: true,
        clientId: test.testClientId,
        xsrfToken: test.testXsrfToken,
        productName: 'Foo Registry'
      }, function() {
        test.sessionMock.login(
            goog.testing.mockmatchers.isFunction).$does(function() {
          test.sessionMock.$reset();
          test.sessionMock.isEppLoggedIn().$returns(true).$anyTimes();
          test.sessionMock.getClientId().$returns(
              test.testClientId).$anyTimes();
          test.sessionMock.$replay();
          test.console.handleHashChange(test.testClientId);
        }).$anyTimes();
      });
  assertTrue(test.console.session.isEppLoggedIn());
  assertNotNull(goog.dom.getElement('domain-registrar-dashboard'));
}


/** Authed user with no path op specified should nav to welcome page. */
function testShowLoginOrDash() {
  registry.registrar.ConsoleTestUtil.visit(test, {
    productName: 'Foo Registry'
  });
  assertNotNull(goog.dom.getElement('domain-registrar-dashboard'));
}


function testNavToResources() {
  registry.registrar.ConsoleTestUtil.visit(test, {
    path: 'resources',
    xsrfToken: test.testXsrfToken,
    technicalDocsUrl: 'http://example.com/techdocs'
  });
  var xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue(xhr.isActive());
  assertEquals('/registrar-settings', xhr.getLastUri());
  assertEquals(test.testXsrfToken,
               xhr.getLastRequestHeaders().get('X-CSRF-Token'));
  xhr.simulateResponse(200, goog.json.serialize({
    status: 'SUCCESS',
    message: 'OK',
    results: [{
      driveFolderId: 'blahblah'
    }]
  }));
  assertContains('blahblah', $('reg-resources-driveLink').getAttribute('href'));
}


function testNavToContactUs() {
  registry.registrar.ConsoleTestUtil.visit(test, {
    path: 'contact-us',
    xsrfToken: test.testXsrfToken,
    productName: 'Domain Registry',
    integrationEmail: 'integration@example.com',
    supportEmail: 'support@example.com',
    announcementsEmail: 'announcement@example.com',
    supportPhoneNumber: '+1 (888) 555 0123'
  });
  var xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue(xhr.isActive());
  assertEquals('/registrar-settings', xhr.getLastUri());
  assertEquals(test.testXsrfToken,
               xhr.getLastRequestHeaders().get('X-CSRF-Token'));
  var passcode = '5-5-5-5-5';
  xhr.simulateResponse(200, goog.json.serialize({
    status: 'SUCCESS',
    message: 'OK',
    results: [{
      phonePasscode: passcode
    }]
  }));
  assertEquals(passcode,
               goog.dom.getTextContent($('domain-registrar-phone-passcode')));
}
