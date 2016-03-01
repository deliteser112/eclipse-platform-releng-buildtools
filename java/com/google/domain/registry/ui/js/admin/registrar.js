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

goog.provide('registry.admin.Registrar');

goog.require('goog.Uri');
goog.require('goog.dom');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.soy');
goog.require('registry.Resource');
goog.require('registry.ResourceComponent');
goog.require('registry.soy.admin.registrar');
goog.require('registry.util');



/**
 * The Registrar class represents server state for registrars and
 * binds UI CRUD operations on them.
 * @param {!registry.Console} console console singleton.
 * @param {string} xsrfToken Security token to pass back to the server.
 * @param {?string} registrarName Optional target registrar name.
 * @constructor
 * @extends {registry.ResourceComponent}
 * @final
 */
registry.admin.Registrar = function(console, xsrfToken, registrarName) {
  registry.admin.Registrar.base(
      this, 'constructor',
      console,
      new registry.Resource(
          new goog.Uri('/_dr/admin/registrar' +
              (registrarName ? ('/' + registrarName) : '')),
          xsrfToken),
      registry.soy.admin.registrar.registrar,
      goog.bind(this.renderSet, this));
};
goog.inherits(registry.admin.Registrar, registry.ResourceComponent);


/**
 * Show the list of registrars.
 * @param {!Element} parentElt In which to render this template.
 * @param {!Object} rspObj Result object from server to show.
 */
registry.admin.Registrar.prototype.renderSet = function(parentElt, rspObj) {
  goog.soy.renderElement(parentElt,
      registry.soy.admin.registrar.registrars,
      rspObj);
  goog.events.listen(goog.dom.getElement('create-button'),
      goog.events.EventType.CLICK,
      goog.bind(this.sendCreate, this));
};


/** @override */
registry.admin.Registrar.prototype.renderItem = function(objArgs) {
  goog.soy.renderElement(goog.dom.getRequiredElement('reg-content'),
      this.itemTmpl,
      {
        item: objArgs,
        readonly: objArgs.readonly
      });
};


/** @override */
registry.admin.Registrar.prototype.sendCreate = function() {
  var args = registry.util.parseForm('create');
  this.resource.create(args,
      goog.bind(this.handleUpdateResponse, this),
      args['clientIdentifier']);
};


/** @override */
registry.admin.Registrar.prototype.setupEditor = function(objArgs) {
  goog.events.listen(goog.dom.getRequiredElement('add-contact-button'),
      goog.events.EventType.CLICK,
      goog.bind(this.addContactInputForm_, this));
  var childNodes = goog.dom.getChildren(
      goog.dom.getRequiredElement('contacts'));
  for (var i = 0; i < childNodes.length; i++) {
    this.enableRemoveButton(childNodes[i]);
  }
};


/** @override */
registry.admin.Registrar.prototype.prepareUpdate = function(modelCopy) {
  var form = registry.util.parseForm('item');
  for (var ndx in form) {
    modelCopy[ndx] = form[ndx];
  }
  var apply = function(obj, ndx, func) {
    if (goog.isDefAndNotNull(obj[ndx])) {
      obj[ndx] = func(obj[ndx]);
    }
  };
  var splitter = function(val) {
    return val.split(',');
  };
  apply(modelCopy, 'billingIdentifier', parseInt);
  apply(modelCopy, 'ianaIdentifier', parseInt);
  apply(modelCopy, 'allowedTlds', splitter);
  apply(modelCopy, 'ipAddressWhitelist', splitter);
};


/**
 * Add a contact input entry form to the page.
 * @private
 */
registry.admin.Registrar.prototype.addContactInputForm_ = function() {
  var contactsContainer =
      goog.dom.getRequiredElement('contacts');
  var childCount = contactsContainer.childNodes.length;
  var newContactDiv = goog.soy.renderAsElement(
      registry.soy.admin.registrar.contactInfo, {
        namePrefix: 'contacts[' + childCount + '].'
      });
  goog.dom.appendChild(contactsContainer, newContactDiv);
  this.enableRemoveButton(newContactDiv);
};
