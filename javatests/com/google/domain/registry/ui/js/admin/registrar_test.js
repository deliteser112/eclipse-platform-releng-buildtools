// Copyright 2016 Google Inc. All Rights Reserved.
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

goog.require('goog.History');
goog.require('goog.dispose');
goog.require('goog.dom');
goog.require('goog.soy');
goog.require('goog.string');
goog.require('goog.testing.MockControl');
goog.require('goog.testing.PropertyReplacer');
goog.require('goog.testing.asserts');
goog.require('goog.testing.jsunit');
goog.require('goog.testing.mockmatchers');
goog.require('goog.testing.net.XhrIo');
goog.require('registry.admin.Console');
goog.require('registry.soy.admin.console');
goog.require('registry.testing');


var $ = goog.dom.getRequiredElement;
var _ = goog.testing.mockmatchers.ignoreArgument;
var stubs = new goog.testing.PropertyReplacer();
var mocks = new goog.testing.MockControl();

var historyMock;
var adminConsole;


function setUp() {
  registry.testing.addToDocument('<div id="test"/>');
  goog.soy.renderElement($('test'), registry.soy.admin.console.main, {
    xsrfToken: 'ignore',
    clientIdentifier: 'ignore',
    user: {
      'id': 'go@daddy.tld',
      'actionHref': 'http://godaddy.com',
      'actionName': 'ignore'
    }
  });
  stubs.setPath('goog.net.XhrIo', goog.testing.net.XhrIo);

  historyMock = mocks.createStrictMock(goog.History);
  mocks.createConstructorMock(goog, 'History')().$returns(historyMock);
  historyMock.addEventListener(_, _, _);
  historyMock.setEnabled(true);

  mocks.$replayAll();
  adminConsole = new registry.admin.Console('༼༎෴ ༎༽');
  mocks.$verifyAll();
}


function tearDown() {
  goog.dispose(adminConsole);
  stubs.reset();
  mocks.$tearDown();
  goog.testing.net.XhrIo.cleanup();
}


function testCollectionView() {
  historyMock.$reset();
  historyMock.getToken().$returns('registrar');
  mocks.$replayAll();
  adminConsole.handleHashChange();
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registrar',
      {op: 'read', args: {}},
      {set: []});
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  mocks.$verifyAll();
  assertNotNull($('clientIdentifier'));
}


function testCreate() {
  testCollectionView();
  var testRegistrar = createTestRegistrar();
  $('clientIdentifier').value = testRegistrar.clientIdentifier;
  $('registrarName').value = testRegistrar.registrarName;
  $('icannReferralEmail').value = testRegistrar.icannReferralEmail;
  $('emailAddress').value = testRegistrar.emailAddress;
  $('localizedAddress.street[0]').value =
      testRegistrar.localizedAddress.street[0];
  $('localizedAddress.city').value = testRegistrar.localizedAddress.city;
  $('localizedAddress.countryCode').value =
      testRegistrar.localizedAddress.countryCode;
  registry.testing.click($('create-button'));

  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registrar/daddy', {
        op: 'create',
        args: testRegistrar
      },
      {results: ['daddy: ok']});

  testRegistrar.state = 'PENDING';
  testRegistrar.lastUpdateTime = '2014-08-11T21:57:58.801Z';
  testRegistrar.creationTime = '2014-08-11T21:57:58.801Z';

  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registrar/daddy',
      {op: 'read', args: {}},
      {item: testRegistrar });
  mocks.$verifyAll();
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  assertEquals('daddy',
               goog.dom.getElementsByTagNameAndClass('h1')[0].innerHTML);
  assertEquals('PENDING', $('state').value);

  historyMock.$reset();
  historyMock.getToken().$returns('registrar');
  mocks.$replayAll();
  adminConsole.handleHashChange();
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registrar',
      {op: 'read', args: {}},
      {set: [testRegistrar]});
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  mocks.$verifyAll();
}


function testItemViewEditSave() {
  testCreate();
  historyMock.$reset();
  historyMock.getToken().$returns('registrar/daddy');
  mocks.$replayAll();

  adminConsole.handleHashChange();
  var testRegistrar = createTestRegistrar();
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registrar/daddy',
      {op: 'read', args: {}},
      {item: testRegistrar });
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  mocks.$verifyAll();

  assertTrue('Form should be read-only.', $('registrarName').readOnly);
  registry.testing.click($('reg-app-btn-edit'));
  assertFalse('Form should be edible.', $('registrarName').readOnly);

  testRegistrar.registrarName = 'GoDaddy';
  testRegistrar.emailAddress = 'new@email.com';
  testRegistrar.icannReferralEmail = 'new@referral.com';
  testRegistrar.state = 'ACTIVE';
  testRegistrar.allowedTlds = 'foo,bar,baz';
  testRegistrar.driveFolderId = 'driveFolderId';
  testRegistrar.phoneNumber = '+1.2345678900';
  testRegistrar.faxNumber = '+1.2345678900';
  testRegistrar.whoisServer = 'blah.blee.foo';
  testRegistrar.blockPremiumNames = true;
  testRegistrar.localizedAddress = {
    street: ['mean', '', ''],
    city: 'NYC',
    state: 'AZ',
    zip: '5555',
    countryCode: 'NZ'
  };
  testRegistrar.clientCertificate = '-----BEGIN CERTIFICATE-----' +
      'MIIDvTCCAqWgAwIBAgIJAK/PgPT0jTwRMA0GCSqGSIb3DQEBCwUAMHUxCzAJBgNV' +
      'BAYTAlVTMREwDwYDVQQIDAhOZXcgWW9yazERMA8GA1UEBwwITmV3IFlvcmsxDzAN' +
      'BgNVBAoMBkdvb2dsZTEdMBsGA1UECwwUZG9tYWluLXJlZ2lzdHJ5LXRlc3QxEDAO' +
      'BgNVBAMMB2NsaWVudDEwHhcNMTUwODI2MTkxODA4WhcNNDMwMTExMTkxODA4WjB1' +
      'MQswCQYDVQQGEwJVUzERMA8GA1UECAwITmV3IFlvcmsxETAPBgNVBAcMCE5ldyBZ' +
      'b3JrMQ8wDQYDVQQKDAZHb29nbGUxHTAbBgNVBAsMFGRvbWFpbi1yZWdpc3RyeS10' +
      'ZXN0MRAwDgYDVQQDDAdjbGllbnQxMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB' +
      'CgKCAQEAvoE/IoFJyzb0dU4NFhL8FYgy+B/GnUd5aA66CMx5xKRMbEAtIgxU8TTO' +
      'W+9jdTsE00Grk3Ct4KdY73CYW+6IFXL4O0K/m5S+uajh+I2UMVZJV38RAIqNxue0' +
      'Egv9M4haSsCVIPcX9b+6McywfYSF1bzPb2Gb2FAQO7Jb0BjlPhPMIROCrbG40qPg' +
      'LWrl33dz+O52kO+DyZEzHqI55xH6au77sMITsJe+X23lzQcMFUUm8moiOw0EKrj/' +
      'GaMTZLHP46BCRoJDAPTNx55seIwgAHbKA2VVtqrvmA2XYJQA6ipdhfKRoJFy8Z8H' +
      'DYsorGtazQL2HhF/5uJD25z1m5eQHQIDAQABo1AwTjAdBgNVHQ4EFgQUParEmiSR' +
      'U/Oqy8hr7k+MBKhZwVkwHwYDVR0jBBgwFoAUParEmiSRU/Oqy8hr7k+MBKhZwVkw' +
      'DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAojsUhF6PtZrStnHBFWNR' +
      'ryzvANB8krZlYeX9Hkqn8zIVfAkpbVmL8aZQ7yj17jSpw47PQh3x5gwA9yc/SS0G' +
      'E1rGuxYH02UGbua8G0+vviSQfLtskPQzK7EIR63WNhHEo/Q9umLJkZ0LguWEBf3L' +
      'q8CoXv2i/RNvqVPcTNp/zCKXJZAa8wAjNRJs834AZj4k5xwyYZ3F8D5PGz+YMOmV' +
      'M9Qd+NdXSC/Qn7HQzFhE8p5elBV35P8oX5dXEfn0S7zOXDenp5JvvLoggOWOcKsq' +
      'KiWDQrsT+TMKmHL94/h4t7FghtQLMzY5SGYJsYTv/LG8tewrz6KRb/Wj3JNojyEw' +
      'Ug==' +
      '-----END CERTIFICATE-----';
  testRegistrar.phonePasscode = '01234';
  testRegistrar.ipAddressWhitelist = '1.1.1.1,2.2.2.2';
  testRegistrar.password = 'yoyoSheep';
  testRegistrar.billingIdentifier = '12345';
  testRegistrar.ianaIdentifier = '11111';
  testRegistrar.url = 'http://yoyo.com';
  testRegistrar.referralUrl = 'http://other.com';
  testRegistrar.contacts = [{
    name: 'Joe',
    emailAddress: 'joe@go.com',
    phoneNumber: '',
    faxNumber: '',
    types: 'ADMIN,TECH',
    gaeUserId: '1234'
  }, {
    name: 'Jane',
    emailAddress: 'joe@go.com',
    phoneNumber: '',
    faxNumber: '',
    types: '',
    gaeUserId: '5432'
  }];

  for (var i in testRegistrar) {
    // Not all keys are present as inputs with id,
    // e.g. clientIdentifier.
    var inputElt = goog.dom.getElement(i);
    if (inputElt) {
      inputElt.value = testRegistrar[i];
    }
    if (i == 'contacts' ||
        i == 'localizedAddress' ||
        i == 'blockPremiumNames') {
      continue;
    }
  }

  if (testRegistrar.blockPremiumNames) {
    $('blockPremiumNames').setAttribute('checked', true);
  }

  var addr = testRegistrar['localizedAddress'];
  $('localizedAddress.street[0]').value = addr.street[0];
  $('localizedAddress.street[1]').value = addr.street[1];
  $('localizedAddress.street[2]').value = addr.street[2];
  $('localizedAddress.city').value = addr.city;
  $('localizedAddress.state').value = addr.state;
  $('localizedAddress.zip').value = addr.zip;
  $('localizedAddress.countryCode').value = addr.countryCode;

  var contacts = testRegistrar['contacts'];
  for (var c = 0; c < contacts.length; c++) {
    registry.testing.click($('add-contact-button'));
    var contact = contacts[c];
    for (var ci in contact) {
      // Form IDs are the full path ref.
      $('contacts[' + c + '].' + ci).value = contact[ci];
    }
  }
  registry.testing.click($('reg-app-btn-save'));

  // Convert string to wire vals for assert comparison.
  testRegistrar.billingIdentifier =
      goog.string.parseInt(testRegistrar.billingIdentifier);
  testRegistrar.ianaIdentifier =
      goog.string.parseInt(testRegistrar.ianaIdentifier);
  testRegistrar.allowedTlds = testRegistrar.allowedTlds.split(',');
  testRegistrar.ipAddressWhitelist =
      testRegistrar.ipAddressWhitelist.split(',');
  // And the readonly field the client adds.
  testRegistrar.readonly = false;

  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registrar/daddy',
      {op: 'update', args: testRegistrar},
      {results: ['daddy: ok']});
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registrar/daddy',
      {op: 'read', args: {}},
      {item: testRegistrar});
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  mocks.$verifyAll();
}


/** @return {!Object.<string, ?>} */
function createTestRegistrar() {
  return {
    clientIdentifier: 'daddy',
    registrarName: 'The Daddy',
    icannReferralEmail: 'lol@sloth.test',
    emailAddress: 'foo@bar.com',
    localizedAddress: {
      street: ['111 8th Ave.'],
      city: 'NYC',
      countryCode: 'NZ'
    }
  };
}
