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

goog.provide('registry.admin.Registry');

goog.require('goog.Uri');
goog.require('goog.dom');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.soy');
goog.require('registry.Resource');
goog.require('registry.ResourceComponent');
goog.require('registry.soy.admin.registry');
goog.require('registry.util');



/**
 * The Registry class respresents server state for registries and
 * binds UI CRUD operations on them.
 * @param {!registry.Console} console console singleton.
 * @param {string} xsrfToken Security token to pass back to the server.
 * @param {?string} tld Optional target target tld.
 * @constructor
 * @extends {registry.ResourceComponent}
 * @final
 */
registry.admin.Registry = function(console, xsrfToken, tld) {
  // XXX: A couple of these args for not complete yet..
  registry.admin.Registry.base(
      this, 'constructor',
      console,
      new registry.Resource(
          new goog.Uri('/_dr/admin/registry' + (tld ? ('/' + tld) : '')),
          xsrfToken),
      registry.soy.admin.registry.registry,
      goog.bind(this.renderSet, this));
};
goog.inherits(registry.admin.Registry, registry.ResourceComponent);


/** @override */
registry.admin.Registry.prototype.renderItem = function(objArgs) {
  goog.soy.renderElement(goog.dom.getRequiredElement('reg-content'),
                         this.itemTmpl,
                         {
                           item: objArgs,
                           readonly: objArgs.readonly
                         });
};


/** @override */
registry.admin.Registry.prototype.processItem = function() {
  // XXX: Server response syntax should be improved.
  var tldStateTransitions = this.model['tldStateTransitions'];
  if (tldStateTransitions) {
    var transArr = tldStateTransitions.split(',');
    tldStateTransitions = '';
    for (var i = 0; i < transArr.length; i++) {
      var trans = transArr[i];
      var parts = trans.split('=');
      var dateTime = parts[0].replace(/[ {}]/, '');
      // XXX: ACTUALLY improve parsing with server response syntax.
      if (parts.length == 1) {
        registry.util.butter('pmy needs to fix this CL');
        continue;
      }
      var state = parts[1].replace(/[ {}]/, '');
      tldStateTransitions += state + ',' + dateTime;
      if (i < transArr.length - 1) {
        tldStateTransitions += ';';
      }
    }
    this.model['tldStateTransitions'] = tldStateTransitions;
  }
};


/**
 * @param {!Element} parentElt In which to render this template.
 * @param {!Object} rspObj Result object from server to show.
 */
registry.admin.Registry.prototype.renderSet = function(parentElt, rspObj) {
  goog.soy.renderElement(parentElt,
                         registry.soy.admin.registry.registries,
                         rspObj);
  goog.events.listen(goog.dom.getElement('create-button'),
                     goog.events.EventType.CLICK,
                     goog.bind(this.sendCreate, this));
};


/** @override */
registry.admin.Registry.prototype.sendCreate = function() {
  var args = registry.util.parseForm('create');
  this.resource.create(args,
                       goog.bind(this.handleUpdateResponse, this),
                       args['newTldName']);
};


/** @override */
registry.admin.Registry.prototype.prepareUpdate = function(modelCopy) {
  var form = registry.util.parseForm('item');
  for (var ndx in form) {
    modelCopy[ndx] = form[ndx];
  }
  var apply = function(obj, ndx, func) {
    if (obj[ndx]) {
      obj[ndx] = func(obj[ndx]);
    }
  };
  var parseStateTransitions = function(s) {
    var transitions = s.split(';');
    var transArr = [];
    for (var i = 0; i < transitions.length; i++) {
      var trans = transitions[i];
      var parts = trans.split(',');
      var args = {};
      args['tldState'] = parts[0];
      args['transitionTime'] = parts[1];
      transArr.push(args);
    }
    return transArr;
  };
  apply(modelCopy, 'tldStateTransitions', parseStateTransitions);
};
