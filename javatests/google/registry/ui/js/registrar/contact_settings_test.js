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

goog.require('goog.array');
goog.require('goog.dispose');
goog.require('goog.dom');
goog.require('goog.soy');
goog.require('goog.testing.MockControl');
goog.require('goog.testing.PropertyReplacer');
goog.require('goog.testing.asserts');
goog.require('goog.testing.jsunit');
goog.require('goog.testing.net.XhrIo');
goog.require('registry.registrar.ConsoleTestUtil');
goog.require('registry.soy.registrar.console');
goog.require('registry.testing');
goog.require('registry.util');


var $ = goog.dom.getRequiredElement;
var stubs = new goog.testing.PropertyReplacer();
var testContact = null;

var test = {
  testXsrfToken: '༼༎෴ ༎༽',
  testClientId: 'testClientId',
  mockControl: new goog.testing.MockControl()
};


function setUp() {
  registry.testing.addToDocument('<div id="test"/>');
  registry.testing.addToDocument('<div class="kd-butterbar"/>');
  testContact = createTestContact();
  goog.soy.renderElement($('test'), registry.soy.registrar.console.main, {
    xsrfToken: test.testXsrfToken,
    username: 'blah',
    logoutUrl: 'omg',
    isAdmin: true,
    clientId: test.testClientId,
    showPaymentLink: false,
    logoFilename: 'logo.png',
    productName: 'Nomulus',
    integrationEmail: 'integration@google.com',
    supportEmail: 'support@google.com',
    announcementsEmail: 'announcements@google.com',
    supportPhoneNumber: '123 456 7890',
    technicalDocsUrl: 'http://example.com/techdocs'
  });
  stubs.setPath('goog.net.XhrIo', goog.testing.net.XhrIo);
  registry.registrar.ConsoleTestUtil.setup(test);
}


function tearDown() {
  goog.dispose(test.console);
  goog.testing.net.XhrIo.cleanup();
  stubs.reset();
  test.mockControl.$tearDown();
}


function testCollectionView() {
  registry.registrar.ConsoleTestUtil.visit(test, {
    path: 'contact-settings',
    xsrfToken: test.testXsrfToken,
    clientId: test.testClientId
  });
  registry.testing.assertReqMockRsp(
      test.testXsrfToken,
      '/registrar-settings',
      {op: 'read', args: {}},
      {
        status: 'SUCCESS',
        message: 'OK',
        results: [{
          contacts: [testContact]
        }]
      }
  );
  assertEquals(1, $('admin-contacts').childNodes.length);
  // XXX: Needs more field testing.
}


function testItemView() {
  registry.registrar.ConsoleTestUtil.visit(test, {
    path: 'contact-settings/test@example.com',
    xsrfToken: test.testXsrfToken,
    clientId: test.testClientId
  });
  registry.testing.assertReqMockRsp(
      test.testXsrfToken,
      '/registrar-settings',
      {op: 'read', args: {}},
      {
        status: 'SUCCESS',
        message: 'OK',
        results: [{
          contacts: [testContact]
        }]
      }
  );
  assertEquals(testContact.name, $('contacts[0].name').value);
  assertEquals(testContact.emailAddress, $('contacts[0].emailAddress').value);
  assertEquals(testContact.phoneNumber, $('contacts[0].phoneNumber').value);
  assertEquals(testContact.faxNumber, $('contacts[0].faxNumber').value);
  // XXX: Types are no longer broken out as individual settings, so relying on
  //      screenshot test.
}


// XXX: Should be hoisted.
function testItemEditButtons() {
  testItemView();
  registry.testing.assertVisible($('reg-app-btns-edit'));
  registry.testing.assertHidden($('reg-app-btns-save'));
  registry.testing.click($('reg-app-btn-edit'));
  registry.testing.assertHidden($('reg-app-btns-edit'));
  registry.testing.assertVisible($('reg-app-btns-save'));
  registry.testing.click($('reg-app-btn-cancel'));
  registry.testing.assertVisible($('reg-app-btns-edit'));
  registry.testing.assertHidden($('reg-app-btns-save'));
}


function testItemEdit() {
  testItemView();
  registry.testing.click($('reg-app-btn-edit'));
  document.forms.namedItem('item').elements['contacts[0].name'].value = 'bob';
  registry.testing.click($('reg-app-btn-save'));
  testContact.name = 'bob';
  registry.testing.assertReqMockRsp(
      test.testXsrfToken,
      '/registrar-settings',
      {
        op: 'update',
        args: {
          contacts: [testContact],
          readonly: false
        }
      },
      {
        status: 'SUCCESS',
        message: 'OK',
        results: [{
          contacts: [testContact]
        }]
      }
  );
  registry.testing.assertObjectEqualsPretty(
      testContact,
      simulateJsonForContact(registry.util.parseForm('item').contacts[0]));
}


function testChangeContactTypes() {
  testItemView();
  registry.testing.click($('reg-app-btn-edit'));
  $('contacts[0].type.admin').removeAttribute('checked');
  $('contacts[0].type.legal').setAttribute('checked', 'checked');
  $('contacts[0].type.marketing').setAttribute('checked', 'checked');
  registry.testing.click($('reg-app-btn-save'));
  testContact.types = 'LEGAL,MARKETING';
  registry.testing.assertReqMockRsp(
      test.testXsrfToken,
      '/registrar-settings',
      {
        op: 'update',
        args: {
          contacts: [testContact],
          readonly: false
        }
      },
      {
        status: 'SUCCESS',
        message: 'OK',
        results: [{
          contacts: [testContact]
        }]
      }
  );
  registry.testing.assertObjectEqualsPretty(
      testContact,
      simulateJsonForContact(registry.util.parseForm('item').contacts[0]));
}


function testOneOfManyUpdate() {
  registry.registrar.ConsoleTestUtil.visit(test, {
    path: 'contact-settings/test@example.com',
    xsrfToken: test.testXsrfToken,
    testClientId: test.testClientId
  });
  var testContacts = [
    createTestContact('new1@asdf.com'),
    testContact,
    createTestContact('new2@asdf.com')
  ];
  registry.testing.assertReqMockRsp(
      test.testXsrfToken,
      '/registrar-settings',
      {op: 'read', args: {}},
      {
        status: 'SUCCESS',
        message: 'OK',
        results: [{
          contacts: testContacts
        }]
      }
  );
  // Edit testContact.
  registry.testing.click($('reg-app-btn-edit'));
  $('contacts[1].type.admin').removeAttribute('checked');
  $('contacts[1].type.legal').setAttribute('checked', 'checked');
  $('contacts[1].type.marketing').setAttribute('checked', 'checked');
  registry.testing.click($('reg-app-btn-save'));

  // Should save them all back, with only testContact changed.
  testContacts[1].types = 'LEGAL,MARKETING';
  registry.testing.assertReqMockRsp(
      test.testXsrfToken,
      '/registrar-settings',
      {op: 'update', args: {contacts: testContacts, readonly: false}},
      {
        status: 'SUCCESS',
        message: 'OK',
        results: [{
          contacts: testContacts
        }]
      }
  );
}


function testDelete() {
  registry.registrar.ConsoleTestUtil.visit(test, {
    path: 'contact-settings/test@example.com',
    xsrfToken: test.testXsrfToken,
    testClientId: test.testClientId
  });
  var testContacts = [
    createTestContact('new1@asdf.com'),
    testContact,
    createTestContact('new2@asdf.com')
  ];
  registry.testing.assertReqMockRsp(
      test.testXsrfToken,
      '/registrar-settings',
      {op: 'read', args: {}},
      {
        status: 'SUCCESS',
        message: 'OK',
        results: [{
          contacts: testContacts
        }]
      }
  );
  // Delete testContact.
  registry.testing.click($('reg-app-btn-edit'));
  registry.testing.click($('reg-app-btn-delete'));

  // Should save them all back, with testContact gone.
  goog.array.removeAt(testContacts, 1);
  registry.testing.assertReqMockRsp(
      test.testXsrfToken,
      '/registrar-settings',
      {op: 'update', args: {contacts: testContacts, readonly: false}},
      {
        status: 'SUCCESS',
        message: 'OK',
        results: [{
          contacts: testContacts
        }]
      }
  );
}


/**
 * @param {string=} opt_email
 * @return {Object}
 */
function createTestContact(opt_email) {
  var nameMail = opt_email || 'test@example.com';
  return {
    name: nameMail,
    emailAddress: nameMail,
    phoneNumber: '+1.2345551234',
    faxNumber: '+1.2345551234',
    visibleInWhoisAsAdmin: false,
    visibleInWhoisAsTech: false,
    types: 'ADMIN'
  };
}


/**
 * Convert parsed formContact to simulated wire form.
 * @param {!Element} contact
 * @return {Object}
 */
function simulateJsonForContact(contact) {
  contact.visibleInWhoisAsAdmin = contact.visibleInWhoisAsAdmin == 'true';
  contact.visibleInWhoisAsTech = contact.visibleInWhoisAsTech == 'true';
  contact.types = '';
  for (var tNdx in contact.type) {
    if (contact.type[tNdx]) {
      if (contact.types.length > 0) {
        contact.types += ',';
      }
      contact.types += ('' + tNdx).toUpperCase();
    }
  }
  delete contact['type'];
  return contact;
}
