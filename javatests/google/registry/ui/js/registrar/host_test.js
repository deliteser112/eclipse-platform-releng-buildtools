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

goog.setTestOnly();

goog.require('goog.History');
goog.require('goog.dispose');
goog.require('goog.dom');
goog.require('goog.soy');
goog.require('goog.testing.MockControl');
goog.require('goog.testing.PropertyReplacer');
goog.require('goog.testing.asserts');
goog.require('goog.testing.jsunit');
goog.require('goog.testing.mockmatchers');
goog.require('goog.testing.net.XhrIo');
goog.require('registry.registrar.Console');
goog.require('registry.soy.registrar.console');
goog.require('registry.testing');


var $ = goog.dom.getRequiredElement;
var _ = goog.testing.mockmatchers.ignoreArgument;
var stubs = new goog.testing.PropertyReplacer();
var mocks = new goog.testing.MockControl();

var historyMock;
var registrarConsole;


function setUp() {
  registry.testing.addToDocument('<div id="test"/>');
  registry.testing.addToDocument('<div class="kd-butterbar"/>');
  goog.soy.renderElement($('test'), registry.soy.registrar.console.main, {
    xsrfToken: 'ignore',
    username: 'jart',
    logoutUrl: 'https://example.com',
    isAdmin: true,
    clientId: 'ignore',
    showPaymentLink: false,
    logoFilename: 'logo.png',
    productName: 'Domain Registry'
  });
  stubs.setPath('goog.net.XhrIo', goog.testing.net.XhrIo);

  historyMock = mocks.createStrictMock(goog.History);
  mocks.createConstructorMock(goog, 'History')().$returns(historyMock);
  historyMock.addEventListener(_, _, _);
  historyMock.setEnabled(true);

  mocks.$replayAll();
  registrarConsole = new registry.registrar.Console('☢', 'jartine');
  mocks.$verifyAll();
}


function tearDown() {
  goog.dispose(registrarConsole);
  stubs.reset();
  mocks.$tearDown();
  goog.testing.net.XhrIo.cleanup();
}


/** Handles EPP login. */
function handleLogin() {
  var request = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <command>' +
      '    <login>' +
      '      <clID>jartine</clID>' +
      '      <pw>undefined</pw>' +
      '      <options>' +
      '        <version>1.0</version>' +
      '        <lang>en</lang>' +
      '      </options>' +
      '      <svcs>' +
      '        <objURI>urn:ietf:params:xml:ns:host-1.0</objURI>' +
      '        <objURI>urn:ietf:params:xml:ns:domain-1.0</objURI>' +
      '        <objURI>urn:ietf:params:xml:ns:contact-1.0</objURI>' +
      '      </svcs>' +
      '    </login>' +
      '    <clTRID>asdf-1235</clTRID>' +
      '  </command>' +
      '</epp>');
  var response = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <response>' +
      '    <result code="2002">' +
      '      <msg>Registrar is already logged in</msg>' +
      '    </result>' +
      '    <trID>' +
      '      <clTRID>asdf-1235</clTRID>' +
      '      <svTRID>ytk1RO+8SmaDQxrTIdulnw==-3</svTRID>' +
      '    </trID>' +
      '  </response>' +
      '</epp>');
  var xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue(xhr.isActive());
  assertEquals('/registrar-xhr', xhr.getLastUri());
  assertEquals('☢', xhr.getLastRequestHeaders().get('X-CSRF-Token'));
  registry.testing.assertXmlEquals(request, xhr.getLastContent());
  xhr.simulateResponse(200, response);
}


function testView() {
  historyMock.$reset();
  historyMock.getToken().$returns('host/ns1.justine.lol').$anyTimes();

  mocks.$replayAll();

  registrarConsole.handleHashChange();
  handleLogin();

  var request = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <command>' +
      '    <info>' +
      '      <host:info xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '        <host:name>ns1.justine.lol</host:name>' +
      '      </host:info>' +
      '    </info>' +
      '    <clTRID>abc-1234</clTRID>' +
      '  </command>' +
      '</epp>');
  var response = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0"' +
      '     xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '  <response>' +
      '    <result code="1000">' +
      '      <msg>Command completed successfully</msg>' +
      '    </result>' +
      '    <resData>' +
      '      <host:infData>' +
      '        <host:name>ns1.justine.lol</host:name>' +
      '        <host:roid>8-roid</host:roid>' +
      '        <host:status s="ok"/>' +
      '        <host:addr ip="v4">8.8.8.8</host:addr>' +
      '        <host:addr ip="v6">feed:a:bee::1</host:addr>' +
      '        <host:clID>justine</host:clID>' +
      '        <host:crID>justine</host:crID>' +
      '        <host:crDate>2014-07-10T02:18:34Z</host:crDate>' +
      '      </host:infData>' +
      '    </resData>' +
      '    <trID>' +
      '      <clTRID>abc-1234</clTRID>' +
      '      <svTRID>EweBEzCZTJirOqRmrtYrAA==-b</svTRID>' +
      '    </trID>' +
      '  </response>' +
      '</epp>');
  var xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue('XHR is inactive.', xhr.isActive());
  assertEquals('/registrar-xhr', xhr.getLastUri());
  assertEquals('application/epp+xml',
               xhr.getLastRequestHeaders().get('Content-Type'));
  assertEquals('☢', xhr.getLastRequestHeaders().get('X-CSRF-Token'));
  registry.testing.assertXmlEquals(request, xhr.getLastContent());
  xhr.simulateResponse(200, response);
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);

  mocks.$verifyAll();

  assertTrue('Form should be read-only.', $('host:chgName').readOnly);
  assertContains('ns1.justine.lol', $('reg-content').innerHTML);
  assertEquals('ns1.justine.lol', $('host:chgName').value);
  assertEquals('8.8.8.8', $('host:addr[0].value').value);
  assertEquals('feed:a:bee::1', $('host:addr[1].value').value);
}


function testEditFirstAddr_ignoreSecond_addThird() {
  testView();

  historyMock.$reset();

  mocks.$replayAll();

  registry.testing.click($('reg-app-btn-edit'));

  assertFalse('Form should be edible.', $('host:addr[0].value').readOnly);
  $('host:addr[0].value').value = '1.2.3.4';
  registry.testing.click($('domain-host-addr-add-button'));
  $('host:addr[2].value').value = 'feed:a:fed::1';

  registry.testing.click($('reg-app-btn-save'));

  var request = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <command>' +
      '    <update>' +
      '      <host:update xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '        <host:name>ns1.justine.lol</host:name>' +
      '        <host:add>' +
      '          <host:addr ip="v4">1.2.3.4</host:addr>' +
      '          <host:addr ip="v6">feed:a:fed::1</host:addr>' +
      '        </host:add>' +
      '        <host:rem>' +
      '          <host:addr ip="v4">8.8.8.8</host:addr>' +
      '        </host:rem>' +
      '      </host:update>' +
      '    </update>' +
      '    <clTRID>abc-1234</clTRID>' +
      '  </command>' +
      '</epp>');
  var response = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <response>' +
      '    <result code="1000">' +
      '      <msg>This world is built from a million lies.</msg>' +
      '    </result>' +
      '    <trID>' +
      '      <clTRID>abc-1234</clTRID>' +
      '      <svTRID>214CjbYuTsijoP8sgyFUNg==-e</svTRID>' +
      '    </trID>' +
      '  </response>' +
      '</epp>');
  var xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue('XHR is inactive.', xhr.isActive());
  assertEquals('/registrar-xhr', xhr.getLastUri());
  assertEquals('☢', xhr.getLastRequestHeaders().get('X-CSRF-Token'));
  registry.testing.assertXmlEquals(request, xhr.getLastContent());
  xhr.simulateResponse(200, response);

  request = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <command>' +
      '    <info>' +
      '      <host:info xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '        <host:name>ns1.justine.lol</host:name>' +
      '      </host:info>' +
      '    </info>' +
      '    <clTRID>abc-1234</clTRID>' +
      '  </command>' +
      '</epp>');
  response = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0"' +
      '     xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '  <response>' +
      '    <result code="1000">' +
      '      <msg>Command completed successfully</msg>' +
      '    </result>' +
      '    <resData>' +
      '      <host:infData>' +
      '        <host:name>ns1.justine.lol</host:name>' +
      '        <host:roid>8-roid</host:roid>' +
      '        <host:status s="ok"/>' +
      '        <host:addr ip="v6">feed:a:bee::1</host:addr>' +
      '        <host:addr ip="v4">1.2.3.4</host:addr>' +
      '        <host:addr ip="v6">feed:a:fed::1</host:addr>' +
      '        <host:clID>justine</host:clID>' +
      '        <host:crID>justine</host:crID>' +
      '        <host:crDate>2014-07-10T02:18:34Z</host:crDate>' +
      '      </host:infData>' +
      '    </resData>' +
      '    <trID>' +
      '      <clTRID>abc-1234</clTRID>' +
      '      <svTRID>EweBEzCZTJirOqRmrtYrAA==-b</svTRID>' +
      '    </trID>' +
      '  </response>' +
      '</epp>');
  xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue('XHR is inactive.', xhr.isActive());
  assertEquals('/registrar-xhr', xhr.getLastUri());
  assertEquals('☢', xhr.getLastRequestHeaders().get('X-CSRF-Token'));
  registry.testing.assertXmlEquals(request, xhr.getLastContent());
  xhr.simulateResponse(200, response);
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);

  mocks.$verifyAll();

  assertTrue('Form should be read-only.', $('host:chgName').readOnly);
  assertContains('ns1.justine.lol', $('reg-content').innerHTML);
  assertEquals('ns1.justine.lol', $('host:chgName').value);
  assertEquals('feed:a:bee::1', $('host:addr[0].value').value);
  assertEquals('1.2.3.4', $('host:addr[1].value').value);
  assertEquals('feed:a:fed::1', $('host:addr[2].value').value);
}


function testCreate() {
  historyMock.$reset();
  historyMock.getToken().$returns('host').$anyTimes();
  mocks.$replayAll();
  registrarConsole.handleHashChange();
  handleLogin();
  mocks.$verifyAll();

  assertFalse('Form should be edible.', $('host:name').readOnly);
  $('host:name').value = 'ns1.example.tld';
  registry.testing.click($('domain-host-addr-add-button'));
  $('host:addr[0].value').value = '192.0.2.2';
  registry.testing.click($('domain-host-addr-add-button'));
  $('host:addr[1].value').value = '192.0.2.29';
  registry.testing.click($('domain-host-addr-add-button'));
  $('host:addr[2].value').value = '1080:0:0:0:8:800:200C:417A';

  historyMock.$reset();
  mocks.$replayAll();

  registry.testing.click($('reg-app-btn-save'));

  var request = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <command>' +
      '    <create>' +
      '      <host:create xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '        <host:name>ns1.example.tld</host:name>' +
      '        <host:addr ip="v4">192.0.2.2</host:addr>' +
      '        <host:addr ip="v4">192.0.2.29</host:addr>' +
      '        <host:addr ip="v6">1080:0:0:0:8:800:200C:417A</host:addr>' +
      '      </host:create>' +
      '    </create>' +
      '    <clTRID>abc-1234</clTRID>' +
      '  </command>' +
      '</epp>');
  var response = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <response>' +
      '    <result code="1000">' +
      '      <msg>Command completed successfully</msg>' +
      '    </result>' +
      '    <resData>' +
      '      <host:creData xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '        <host:name>ns1.example.tld</host:name>' +
      '        <host:crDate>1999-04-03T22:00:00.0Z</host:crDate>' +
      '      </host:creData>' +
      '    </resData>' +
      '    <trID>' +
      '      <clTRID>abc-1234</clTRID>' +
      '      <svTRID>EweBEzCZTJirOqRmrtYrAA==-b</svTRID>' +
      '    </trID>' +
      '  </response>' +
      '</epp>');
  var xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue('XHR is inactive.', xhr.isActive());
  assertEquals('/registrar-xhr', xhr.getLastUri());
  assertEquals('☢', xhr.getLastRequestHeaders().get('X-CSRF-Token'));
  registry.testing.assertXmlEquals(request, xhr.getLastContent());
  xhr.simulateResponse(200, response);

  request = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <command>' +
      '    <info>' +
      '      <host:info xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '        <host:name>ns1.example.tld</host:name>' +
      '      </host:info>' +
      '    </info>' +
      '    <clTRID>abc-1234</clTRID>' +
      '  </command>' +
      '</epp>');
  response = registry.testing.loadXml(
      '<?xml version="1.0"?>' +
      '<epp xmlns="urn:ietf:params:xml:ns:epp-1.0">' +
      '  <response>' +
      '    <result code="1000">' +
      '      <msg>Command completed successfully</msg>' +
      '    </result>' +
      '    <resData>' +
      '      <host:infData xmlns:host="urn:ietf:params:xml:ns:host-1.0">' +
      '        <host:name>ns1.example.tld</host:name>' +
      '        <host:roid>NS1_EXAMPLE1-REP</host:roid>' +
      '        <host:status s="linked"/>' +
      '        <host:status s="clientUpdateProhibited"/>' +
      '        <host:addr ip="v4">192.0.2.2</host:addr>' +
      '        <host:addr ip="v4">192.0.2.29</host:addr>' +
      '        <host:addr ip="v6">1080:0:0:0:8:800:200C:417A</host:addr>' +
      '        <host:clID>TheRegistrar</host:clID>' +
      '        <host:crID>NewRegistrar</host:crID>' +
      '        <host:crDate>1999-04-03T22:00:00.0Z</host:crDate>' +
      '        <host:upID>NewRegistrar</host:upID>' +
      '        <host:upDate>1999-12-03T09:00:00.0Z</host:upDate>' +
      '        <host:trDate>2000-04-08T09:00:00.0Z</host:trDate>' +
      '      </host:infData>' +
      '    </resData>' +
      '    <trID>' +
      '      <clTRID>abc-1234</clTRID>' +
      '      <svTRID>EweBEzCZTJirOqRmrtYrAA==-b</svTRID>' +
      '    </trID>' +
      '  </response>' +
      '</epp>');
  xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue('XHR is inactive.', xhr.isActive());
  assertEquals('/registrar-xhr', xhr.getLastUri());
  assertEquals('☢', xhr.getLastRequestHeaders().get('X-CSRF-Token'));
  registry.testing.assertXmlEquals(request, xhr.getLastContent());
  xhr.simulateResponse(200, response);
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);

  mocks.$verifyAll();

  assertTrue('Form should be read-only.', $('host:chgName').readOnly);
  assertEquals('ns1.example.tld', $('host:chgName').value);
  assertEquals('192.0.2.2', $('host:addr[0].value').value);
  assertEquals('192.0.2.29', $('host:addr[1].value').value);
  assertEquals('1080:0:0:0:8:800:200C:417A', $('host:addr[2].value').value);
}
