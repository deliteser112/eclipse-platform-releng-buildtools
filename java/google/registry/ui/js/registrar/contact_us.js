// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

goog.provide('registry.registrar.ContactUs');

goog.require('goog.Uri');
goog.require('goog.dom');
goog.require('registry.Resource');
goog.require('registry.ResourceComponent');
goog.require('registry.soy.registrar.console');

goog.forwardDeclare('registry.registrar.Console');



/**
 * Contact Us page.
 * @param {!registry.registrar.Console} console
 * @param {string} xsrfToken Security token to pass back to the server.
 * @constructor
 * @extends {registry.ResourceComponent}
 * @final
 */
registry.registrar.ContactUs = function(console, xsrfToken) {
  registry.registrar.ContactUs.base(
      this,
      'constructor',
      console,
      new registry.Resource(new goog.Uri('/registrar-settings'), xsrfToken),
      registry.soy.registrar.console.contactUs,
      null);
};
goog.inherits(registry.registrar.ContactUs, registry.ResourceComponent);


/** @override */
registry.registrar.ContactUs.prototype.bindToDom = function(id) {
  registry.registrar.ContactUs.base(this, 'bindToDom', '');
  goog.dom.removeChildren(goog.dom.getRequiredElement('reg-app-buttons'));
};
