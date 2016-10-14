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

goog.provide('registry.registrar.ConsoleTestUtil');
goog.setTestOnly('registry.registrar.ConsoleTestUtil');

goog.require('goog.History');
goog.require('goog.asserts');
goog.require('goog.dom.xml');
goog.require('goog.testing.mockmatchers');
goog.require('registry.registrar.Console');
goog.require('registry.registrar.EppSession');
goog.require('registry.xml');


/**
 * Utility method that attaches mocks to a {@code TestCase}.  This was
 * originally in the ctor for ConsoleTest and should simply be
 * inherited but jstd_test breaks inheritance in test cases.
 * @param {Object} test the test case to configure.
 */
registry.registrar.ConsoleTestUtil.setup = function(test) {
  test.historyMock = test.mockControl.createLooseMock(goog.History, true);
  test.sessionMock = test.mockControl.createLooseMock(
      registry.registrar.EppSession, true);
  /** @suppress {missingRequire} */
  test.mockControl.createConstructorMock(goog, 'History')()
      .$returns(test.historyMock);
  /** @suppress {missingRequire} */
  test.mockControl
      .createConstructorMock(registry.registrar, 'EppSession')(
          goog.testing.mockmatchers.isObject,
          goog.testing.mockmatchers.isString,
          goog.testing.mockmatchers.isString)
      .$returns(test.sessionMock);
};


/**
 * Simulates visiting a page on the console.  Sets path, then mocks
 * session and calls {@code handleHashChange_}.
 * @param {Object} test the test case to configure.
 * @param {Object=} opt_args may include path, isEppLoggedIn.
 * @param {Function=} opt_moar extra setup after called just before
 *     {@code $replayAll}.  See memegen/3437690.
 */
registry.registrar.ConsoleTestUtil.visit = function(
    test, opt_args, opt_moar) {
  opt_args = opt_args || {};
  opt_args.path = opt_args.path || '';
  opt_args.clientId = opt_args.clientId || 'dummyRegistrarId';
  opt_args.xsrfToken = opt_args.xsrfToken || 'dummyXsrfToken';
  if (opt_args.isEppLoggedIn === undefined) {
    opt_args.isEppLoggedIn = true;
  }
  test.historyMock.$reset();
  test.sessionMock.$reset();
  test.historyMock.getToken().$returns(opt_args.path).$anyTimes();
  test.sessionMock.isEppLoggedIn().$returns(opt_args.isEppLoggedIn).$anyTimes();
  test.sessionMock.getClientId().$returns(opt_args.isEppLoggedIn ?
      opt_args.clientId : null).$anyTimes();
  if (opt_args.rspXml) {
    test.sessionMock
        .send(goog.testing.mockmatchers.isString,
              goog.testing.mockmatchers.isFunction)
        .$does(function(args, cb) {
          // XXX: Args should be checked.
          var xml = goog.dom.xml.loadXml(opt_args.rspXml);
          goog.asserts.assert(xml != null);
          cb(registry.xml.convertToJson(xml));
        }).$anyTimes();
  }
  if (opt_moar) {
    opt_moar();
  }
  test.mockControl.$replayAll();
  /** @type {!registry.registrar.Console} */
  test.console = new registry.registrar.Console(opt_args);
  // XXX: Should be triggered via event passing.
  test.console.handleHashChange();
  test.mockControl.$verifyAll();
};
