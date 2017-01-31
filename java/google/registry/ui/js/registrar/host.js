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

goog.provide('registry.registrar.Host');

goog.require('registry.registrar.XmlResourceComponent');
goog.require('registry.soy.registrar.host');
goog.require('registry.soy.registrar.hostepp');

goog.forwardDeclare('registry.registrar.Console');



/**
 * CRUD for EPP host objects.
 * @param {!registry.registrar.Console} console
 * @constructor
 * @extends {registry.registrar.XmlResourceComponent}
 * @final
 */
registry.registrar.Host = function(console) {
  registry.registrar.Host.base(
      this, 'constructor',
      registry.soy.registrar.host.item,
      registry.soy.registrar.hostepp,
      console);
};
goog.inherits(registry.registrar.Host,
              registry.registrar.XmlResourceComponent);


/** @override */
registry.registrar.Host.prototype.processItem = function() {
  this.model.item = this.model['epp']['response']['resData']['host:infData'];
  if (this.model.item['host:addr']) {
    if (!goog.isArray(this.model.item['host:addr'])) {
      this.model.item['host:addr'] = [this.model.item['host:addr']];
    }
  } else {
    this.model.item['host:addr'] = [];
  }
};


/** @override */
registry.registrar.Host.prototype.setupEditor = function(objArgs) {
  this.typeCounts['host-addr'] =
      objArgs.item['host:addr'] ? objArgs.item['host:addr'].length : 0;
  this.addRemBtnHandlers('host-addr', goog.bind(function() {
    return 'host:addr[' + this.typeCounts['host-addr'] + ']';
  }, this));

  this.formInputRowRemovable(document.querySelectorAll('input[readonly]'));
};


/** @override */
registry.registrar.Host.prototype.prepareCreate = function(params) {
  params.nextId = params.item['host:name'];
  return registry.soy.registrar.hostepp.create(params).toString();
};


/** @override */
registry.registrar.Host.prototype.prepareUpdate = function(params) {
  var form = params.item;
  var addAddrs = [];
  var remAddrs = [];
  if (form['host:addr']) {
    var oldAddrs = form['host:oldAddr'] || [];
    var newAddrs = form['host:addr'];
    var length = Math.max(oldAddrs.length, newAddrs.length);
    for (var i = 0; i < length; i++) {
      if (i >= oldAddrs.length) {
        addAddrs.push(newAddrs[i]['value']);
      } else if (i >= newAddrs.length) {
        remAddrs.push(oldAddrs[i]['value']);
      } else {
        if (newAddrs[i]['value'] == oldAddrs[i]['value']) {
          // Do nothing.
        } else if (newAddrs[i]['value'] == '') {
          remAddrs.push(oldAddrs[i]['value']);
        } else {
          remAddrs.push(oldAddrs[i]['value']);
          addAddrs.push(newAddrs[i]['value']);
        }
      }
    }
  }
  params.addAddrs = addAddrs;
  params.remAddrs = remAddrs;
  params.nextId = form['host:chgName'];
  return registry.soy.registrar.hostepp.update(params).toString();
};
