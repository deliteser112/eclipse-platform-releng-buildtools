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

goog.provide('registry.registrar.Console');

goog.require('goog.dispose');
goog.require('goog.dom');
goog.require('goog.dom.classlist');
goog.require('goog.net.XhrIo');
goog.require('registry.Console');
goog.require('registry.registrar.Contact');
goog.require('registry.registrar.ContactSettings');
goog.require('registry.registrar.ContactUs');
goog.require('registry.registrar.Dashboard');
goog.require('registry.registrar.Domain');
goog.require('registry.registrar.EppSession');
goog.require('registry.registrar.Host');
goog.require('registry.registrar.Payment');
goog.require('registry.registrar.Resources');
goog.require('registry.registrar.SecuritySettings');
goog.require('registry.registrar.WhoisSettings');
goog.require('registry.util');

goog.forwardDeclare('registry.Component');



/**
 * The Registrar Console.
 * @param {!Object} params Parameters to be passed into templates.  These are
 *   a combination of configurable parameters (e.g. phone number) and
 *   user/session/registrar specific parameters.  See
 *   registrar/Console.soy#.main for expected contents.
 * @constructor
 * @extends {registry.Console}
 * @final
 */
registry.registrar.Console = function(params) {
  registry.registrar.Console.base(
      this, 'constructor',
      new registry.registrar.EppSession(this, params.xsrfToken,
                                        params.clientId));

  /**
   * Component that's currently embedded in the page.
   * @type {?registry.Component}
   * @private
   */
  this.component_ = null;

  // XXX: This was in parent ctor but was triggering event dispatching before
  //      ready here.
  this.history.setEnabled(true);

  /**
   * @type {!Object}
   */
  this.params = params;

  /**
   * Last active nav element.
   * @type {Element}
   */
  this.lastActiveNavElt;

  /**
   * @type {!Object.<string, function(new:registry.Component,
   *                                  !registry.registrar.Console,
   *                                  string)>}
   */
  this.pageMap = {};
  this.pageMap['security-settings'] = registry.registrar.SecuritySettings;
  this.pageMap['contact-settings'] = registry.registrar.ContactSettings;
  this.pageMap['whois-settings'] = registry.registrar.WhoisSettings;
  this.pageMap['contact-us'] = registry.registrar.ContactUs;
  this.pageMap['resources'] = registry.registrar.Resources;
  this.pageMap['contact'] = registry.registrar.Contact;
  this.pageMap['payment'] = registry.registrar.Payment;
  this.pageMap['domain'] = registry.registrar.Domain;
  this.pageMap['host'] = registry.registrar.Host;
  this.pageMap[''] = registry.registrar.Dashboard;
};
goog.inherits(registry.registrar.Console, registry.Console);


/**
 * Changes the content area depending on hash path.
 *
 * <p>Hash path is expected to be of the form:
 *
 * <pre>
 *   #type/id
 * </pre>
 *
 * <p>The {@code id} part may be appended by {@code ()} to specify the target
 * should be a resource create page.
 *
 * @override
 */
registry.registrar.Console.prototype.handleHashChange = function() {
  var hashToken = this.history.getToken();
  // On page reloads, opening a new tab, etc. it's possible that the
  // session cookie for a logged-in session exists, but the
  // this.session is not yet aware, so come back here after syncing.
  //
  // XXX: Method should be refactored to avoid this 2-stage behavior.
  if (!this.session.isEppLoggedIn()) {
    this.session.login(goog.bind(this.handleHashChange, this));
    return;
  }

  // Otherwise, a resource operation.
  var parts = hashToken.split('/');
  var type = '';
  var id = '';
  if (parts.length >= 1) {
    type = parts[0];
  }
  if (parts.length == 2) {
    id = parts[1];
  }

  goog.net.XhrIo.cleanup();

  var componentCtor = this.pageMap[type];
  if (componentCtor == undefined) {
    componentCtor = this.pageMap[''];
  }
  var oldComponent = this.component_;
  this.component_ = new componentCtor(this, this.params.xsrfToken);
  this.registerDisposable(this.component_);
  this.component_.basePath = type;
  this.component_.bindToDom(id);

  this.changeNavStyle();

  goog.dispose(oldComponent);
};


/** Change nav style. */
registry.registrar.Console.prototype.changeNavStyle = function() {
  var hashToken = this.history.getToken();
  // Path except id
  var slashNdx = hashToken.lastIndexOf('/');
  slashNdx = slashNdx == -1 ? hashToken.length : slashNdx;
  var regNavlist = goog.dom.getRequiredElement('reg-navlist');
  var path = hashToken.substring(0, slashNdx);
  var active = regNavlist.querySelector('a[href="/registrar#' + path + '"]');
  if (goog.isNull(active)) {
    registry.util.log('Unknown path or path form in changeNavStyle.');
    return;
  }
  if (this.lastActiveNavElt) {
    goog.dom.classlist.remove(
        this.lastActiveNavElt, goog.getCssName('domain-active-nav'));
  }
  goog.dom.classlist.add(active, goog.getCssName('domain-active-nav'));
  this.lastActiveNavElt = active;
};
