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

goog.provide('registry.registrar.XmlResourceComponent');

goog.require('goog.dom');
goog.require('goog.dom.TagName');
goog.require('goog.dom.classlist');
goog.require('registry.EditItem');
goog.require('registry.util');

goog.forwardDeclare('registry.registrar.Console');



/**
 * The ResourceComponent class respresents server state for a named
 * resource and binds UI CRUD operations on it, or its constituent
 * collection.
 * @param {function()} itemTmpl
 * @param {!Object} eppTmpls Epp xml templates for info requests.
 * @param {!registry.registrar.Console} console
 * @constructor
 * @extends {registry.EditItem}
 */
registry.registrar.XmlResourceComponent = function(
    itemTmpl, eppTmpls, console) {
  registry.registrar.XmlResourceComponent.base(
      this, 'constructor', console, itemTmpl);

  /** @type {!Object} */
  this.eppTmpls = eppTmpls;
};
goog.inherits(registry.registrar.XmlResourceComponent, registry.EditItem);


/** @override */
registry.registrar.XmlResourceComponent.prototype.bindToDom =
    function(id) {
  // XXX: EPP resources still use null state.
  registry.registrar.XmlResourceComponent.base(
      this, 'bindToDom', (id || ''));
  if (id) {
    this.fetchItem(id);
  } else {
    // Start edit of empty object.
    this.edit();
  }
};


/** @override */
registry.registrar.XmlResourceComponent.prototype.fetchItem = function(id) {
  var queryParams = {id: id, clTrid: 'abc-1234'};
  var xml = this.prepareFetch(queryParams);
  this.console.session.send(xml, goog.bind(this.handleFetchItem, this, id));
};


/** @override */
registry.registrar.XmlResourceComponent.prototype.handleFetchItem =
    function(id, rsp) {
  this.model = rsp;
  var result = rsp['epp']['response']['result'];
  var resCode = result['@code'];
  // XXX: Should use enum.
  if (resCode == 1000) { // OK
    this.model.readonly = true;
    this.processItem();
    this.renderItem(this.model);
  } else if (resCode == 2303) { // Missing
    registry.util.butter(
        'Could not find: "' + id + '".  Please enter as "type/id"');
  } else {
    // includes general failure.
    registry.util.butter(resCode + ':' + result['msg']['keyValue']);
  }
};


/**
 * Sublcasses should override to populate create queryParams with form
 * fields as needed.  {@code queryParams.nextId} MUST be set to the
 * new object's ID.
 * @param {!Object} queryParams
 */
registry.registrar.XmlResourceComponent.prototype.prepareCreate =
    goog.abstractMethod;


/**
 * Calls prepareCreate with template params and then send the returned
 * XML to the server
 * @override
 */
registry.registrar.XmlResourceComponent.prototype.sendCreate = function() {
  var form = registry.util.parseForm('item');
  var queryParams = {item: form, clTrid: 'abc-1234'};
  var xml = this.prepareCreate(queryParams);
  this.nextId = queryParams.nextId;
  this.console.session.send(xml, goog.bind(this.handleUpdateResponse, this));
};


/**
 * Calls prepareUpdate with template params and then send the returned
 * XML to the server.
 * @override
 */
registry.registrar.XmlResourceComponent.prototype.sendUpdate = function() {
  var form = registry.util.parseForm('item');
  var queryParams = {item: form, clTrid: 'abc-1234'};
  var xml = this.prepareUpdate(queryParams);
  this.nextId = queryParams.nextId;
  this.console.session.send(xml, goog.bind(this.handleUpdateResponse, this));
};


/**
 * Sublcasses should override to populate fetch queryParams with form
 * fields as needed.
 * @param {!Object} queryParams
 * @return {string} EPP xml string
 */
registry.registrar.XmlResourceComponent.prototype.prepareFetch =
    function(queryParams) {
  return this.eppTmpls.info(queryParams).toString();
};


/** @override */
registry.registrar.XmlResourceComponent.prototype.handleUpdateResponse =
    function(rsp) {
  var result = rsp['epp']['response']['result'];
  if (result['@code'] == 1000) {
    // XXX: Consider timer, probably just as a seconds arg with impl in butter.
    registry.util.butter('Saved.');
    this.fetchItem(this.nextId || '');
    this.nextId = null;
    this.toggleEdit();
  } else {
    registry.util.butter(result['msg']['keyValue']);
  }
  return rsp;
};


/**
 * Helper to add add/remove hosts/contacts on queryParams:
 *
 *   queryParams[op + fieldType] = formFields.
 *
 * @param {Array.<Object>} formFields named form item representations
 *     that have an associated {op: (add|rem)} attribute.
 * @param {string} fieldType capitalized pluralized type name, e.g. "Contacts".
 * @param {!Object} queryParams
 */
registry.registrar.XmlResourceComponent.prototype.addRem =
    function(formFields, fieldType, queryParams) {
  var add = [];
  var rem = [];
  for (var i = 0; i < formFields.length; i++) {
    var formField = formFields[i];
    var op = formField['op'];
    switch (op) {
      case 'add':
        add.push(formField);
        break;
      case 'rem':
        rem.push(formField);
        break;
      default:
        registry.util.log(
            'Unknown op attribute ' + op + ' on form field:',
            formField);
    }
  }
  if (add.length > 0) {
    queryParams['add' + fieldType] = add;
  }
  if (rem.length > 0) {
    queryParams['rem' + fieldType] = rem;
  }
};


/**
 * Make the given form input rows removable.
 * @param {NodeList} elts array of input elements.
 * @param {function()=} opt_onclickCb called when remove button is clicked.
 */
registry.registrar.XmlResourceComponent.prototype.formInputRowRemovable =
    function(elts, opt_onclickCb) {
  for (var i = 0; i < elts.length; i++) {
    var parent = goog.dom.getParentElement(elts[i]);
    this.appendRemoveBtn(parent, goog.partial(function(elt) {
      var eppRemInput = goog.dom.createDom(goog.dom.TagName.INPUT, {
        'name': elt.name.replace(/\.value/, '.op'),
        'value': 'rem',
        'type': 'hidden'
      });
      goog.dom.appendChild(goog.dom.getParentElement(elt), eppRemInput);
      goog.dom.classlist.add(
          goog.dom.getParentElement(
              goog.dom.getParentElement(elt)), goog.getCssName('hidden'));
    }, elts[i]));
  }
};
