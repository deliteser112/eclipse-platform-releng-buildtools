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

goog.provide('registry.registrar.EppSession');

goog.require('goog.Uri');
goog.require('registry.Session');
goog.require('registry.soy.registrar.epp');
goog.require('registry.util');
goog.require('registry.xml');

goog.forwardDeclare('registry.registrar.Console');



/**
 * Session state for console.
 * @param {!registry.registrar.Console} console
 * @param {string} xsrfToken Populated by server-side soy template.
 * @param {string} clientId The logged in GAE user.
 * @constructor
 * @extends {registry.Session}
 * @final
 */
registry.registrar.EppSession = function(console, xsrfToken, clientId) {
  registry.registrar.EppSession.base(
      this, 'constructor', new goog.Uri('/registrar-xhr'), xsrfToken,
      registry.Session.ContentType.EPP);

  /**
   * @type {!registry.registrar.Console}
   */
  this.console = console;

  /**
   * @type {!boolean}
   * @private
   */
  this.isEppLoggedIn_ = false;

  /**
   * @type {string}
   * @private
   */
  this.clientId_ = clientId;
};
goog.inherits(registry.registrar.EppSession, registry.Session);


/**
 * Whether the session has received an EPP success response to an EPP
 * login attempt.
 * @return {boolean} Whether the user is logged into an EPP session.
 */
registry.registrar.EppSession.prototype.isEppLoggedIn = function() {
  return this.isEppLoggedIn_;
};


/**
 * Get the clientId if the user is logged in, or throw Error.
 * @throws Error if the user is not logged in.
 * @return {string} the clientId.
 */
registry.registrar.EppSession.prototype.getClientId = function() {
  return this.clientId_;
};


/**
 * Login or display butterbar info about error.
 * @param {function()} successCb to be called on success.
 */
registry.registrar.EppSession.prototype.login = function(successCb) {
  var eppArgs = {clId: this.clientId_, clTrid: 'asdf-1235'};
  this.send(
      registry.soy.registrar.epp.login(eppArgs).getContent(),
      goog.bind(function(xml) {
        var result = xml['epp']['response']['result'];
        var eppCode = result['@code'];
        if (eppCode == '1000' || eppCode == '2002') {
          // Success || Already logged in.
          this.isEppLoggedIn_ = true;
          successCb();
        } else {
          // Failure.
          this.isEppLoggedIn_ = false;
          registry.util.butter('login error: ' + eppCode);
        }
      }, this));
};


/**
 * Logout or display butterbar info about error.
 */
registry.registrar.EppSession.prototype.logout = function() {
  this.send(
      registry.soy.registrar.epp.logout(
          {clTrid: 'asdf-1235'}).getContent(),
      goog.bind(function(xml) {
        var result = xml['epp']['response']['result'];
        var eppCode = result['@code'];
        registry.util.butter(
            'logout ' + eppCode + ': ' + result['msg']['keyValue']);
        // Going to be safe here and force a login either way.
        this.isEppLoggedIn_ = false;
      }, this));
};


/**
 * Send xml to the server.
 * @param {string} xml Request document.
 * @param {function(!Object)} callback For XhrIo result throws.
 */
registry.registrar.EppSession.prototype.send = function(xml, callback) {
  var toXmlJsonCb = function(rspXml) {
    callback(registry.xml.convertToJson(rspXml));
  };
  this.sendXhrIo(xml, toXmlJsonCb);
};
