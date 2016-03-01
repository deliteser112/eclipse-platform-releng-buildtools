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
    clientId: 'ignore',
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
  historyMock.getToken().$returns('registry');
  mocks.$replayAll();
  adminConsole.handleHashChange();
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registry',
      {op: 'read', args: {}},
      {set: []});
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  mocks.$verifyAll();
  assertNotNull($('newTldName'));
}


/** Creates test registry. */
function createTestRegistry() {
  return {
    addGracePeriod: 'PT432000S',
    autoRenewGracePeriod: 'PT3888000S',
    automaticTransferLength: 'PT432000S',
    name: 'foo',
    pendingDeleteLength: 'PT432000S',
    redemptionGracePeriod: 'PT2592000S',
    renewGracePeriod: 'PT432000S',
    state: 'PREDELEGATION',
    tldStateTransitions: '{1970-01-01T00:00:00.000Z=PREDELEGATION}',
    transferGracePeriod: 'PT432000S'
  };
}


function testCreate() {
  testCollectionView();
  $('newTldName').value = 'foo';
  registry.testing.click($('create-button'));
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registry/foo', {
        op: 'create',
        args: {
          newTldName: 'foo'
        }
      },
      {results: ['foo: ok']});
  var testReg = createTestRegistry();
  testReg.creationTime = '2014-08-07T20:35:39.142Z';
  testReg.lastUpdateTime = '2014-08-07T20:35:39.142Z';

  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registry/foo',
      {op: 'read', args: {}},
      {item: testReg });
  mocks.$verifyAll();
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  assertEquals('foo', goog.dom.getElementsByTagNameAndClass('h1')[0].innerHTML);

  historyMock.$reset();
  historyMock.getToken().$returns('registry');
  mocks.$replayAll();
  adminConsole.handleHashChange();
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registry',
      {op: 'read', args: {}},
      {set: [testReg]});
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  mocks.$verifyAll();
}


function testItemViewEditSave() {
  testCreate();
  historyMock.$reset();
  historyMock.getToken().$returns('registry/foo');
  mocks.$replayAll();

  adminConsole.handleHashChange();
  var testReg = createTestRegistry();
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registry/foo',
      {op: 'read', args: {}},
      {item: testReg });
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  mocks.$verifyAll();

  assertTrue('Form should be read-only.', $('addGracePeriod').readOnly);
  registry.testing.click($('reg-app-btn-edit'));
  assertFalse('Form should be editable.', $('addGracePeriod').readOnly);

  // Edit state
  testReg.tldStateTransitions = 'GENERAL_AVAILABILITY,1970-01-01T00:00:00.000Z';
  for (var i in testReg) {
    // Not all keys are present as inputs with id,
    // e.g. name.
    var inputElt = goog.dom.getElement(i);
    if (inputElt) {
      inputElt.value = testReg[i];
    }
  }
  registry.testing.click($('reg-app-btn-save'));

  // Convert string to wire vals.
  var tldStateTransitionStr = testReg.tldStateTransitions;
  testReg.tldStateTransitions = [{
    tldState: 'GENERAL_AVAILABILITY',
    transitionTime: '1970-01-01T00:00:00.000Z'
  }];
  // And the readonly field the client adds.
  testReg.readonly = false;

  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registry/foo',
      {op: 'update', args: testReg},
      {results: ['foo: ok']});

  // Restore the stringified version.
  testReg.tldStateTransitions = tldStateTransitionStr;
  registry.testing.assertReqMockRsp(
      '༼༎෴ ༎༽',
      '/_dr/admin/registry/foo',
      {op: 'read', args: {}},
      {item: testReg});
  assertEquals('We require more vespene gas.',
               0, goog.testing.net.XhrIo.getSendInstances().length);
  mocks.$verifyAll();
}
