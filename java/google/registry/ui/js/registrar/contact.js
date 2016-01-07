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

goog.provide('registry.registrar.Contact');

goog.require('goog.dom');
goog.require('registry.registrar.XmlResourceComponent');
goog.require('registry.soy.registrar.contact');
goog.require('registry.soy.registrar.contactepp');

goog.forwardDeclare('registry.registrar.Console');



/**
 * The {@code Contact} class respresents a registry contact object and
 * binds UI CRUD operations to it.
 * @param {!registry.registrar.Console} console the
 *     console singleton.
 * @constructor
 * @extends {registry.registrar.XmlResourceComponent}
 * @final
 */
registry.registrar.Contact = function(console) {
  registry.registrar.Contact.base(
      this, 'constructor',
      registry.soy.registrar.contact.item,
      registry.soy.registrar.contactepp,
      console);
};
goog.inherits(registry.registrar.Contact,
              registry.registrar.XmlResourceComponent);


/** @override */
registry.registrar.Contact.prototype.processItem = function() {
  this.model.item = this.model['epp']['response']['resData']['contact:infData'];
  if (!goog.isArray(this.model.item['contact:postalInfo'])) {
    this.model.item['contact:postalInfo'] =
        [this.model.item['contact:postalInfo']];
  }

  // XXX: Is this code necessary?
  var fixPlus = function(val) {
    var str = (val || '') + '';
    if (str == '' || str.match(/\+.*/)) {
      return str;
    } else {
      return '+' + str;
    }
  };

  // Both of these are optional.
  if (this.model.item['contact:voice']) {
    this.model.item['contact:voice']['keyValue'] =
        fixPlus(this.model.item['contact:voice']['keyValue']);
  }
  if (this.model.item['contact:voice']) {
    this.model.item['contact:fax']['keyValue'] =
        fixPlus(this.model.item['contact:fax']['keyValue']);
  }
};


/** @override */
registry.registrar.Contact.prototype.setupEditor = function(objArgs) {
  // For now always keep the first contact and make it i18n. Toggle button
  // disables to enforce state.
  //
  // XXX: Should be simplified to make more modular.
  var postalElt = goog.dom.getRequiredElement('contact-postalInfo');
  var addPostalInfoBtn = goog.dom.getRequiredElement(
      'domain-contact-postalInfo-add-button');
  this.typeCounts['contact-postalInfo'] = postalElt.childNodes.length;
  // 4 child nodes means both addresses are present:
  //     2 data tables, the footer id elt and a hidden input.
  var setupRemoveBtns = this.typeCounts['contact-postalInfo'] == 4;
  if (setupRemoveBtns) {
    this.appendRemoveBtn(/** @type {!Element} */ (postalElt.childNodes[0]));
    this.appendRemoveBtn(/** @type {!Element} */ (postalElt.childNodes[1]));
  } else {
    addPostalInfoBtn.removeAttribute('disabled');
  }

  this.addRemBtnHandlers(
      'contact-postalInfo',
      function() {
        return 'contact:postalInfo[1].contact:';
      },
      function() {
        addPostalInfoBtn.setAttribute('disabled', true);
        return null;
      },
      registry.soy.registrar.contact.postalInfo,
      {
        item: {},
        localized: true,
        itemPrefix: 'contact:',
        namePrefix: 'contact:postalInfo[1].contact:'
      },
      setupRemoveBtns);
};


/** @override */
registry.registrar.Contact.prototype.prepareCreate = function(params) {
  params.nextId = params.item['contact:id'];
  return registry.soy.registrar.contactepp.create(params).toString();
};


/** @override */
registry.registrar.Contact.prototype.prepareUpdate = function(params) {
  params.nextId = params.item['contact:id'];
  return registry.soy.registrar.contactepp.update(params).toString();
};
