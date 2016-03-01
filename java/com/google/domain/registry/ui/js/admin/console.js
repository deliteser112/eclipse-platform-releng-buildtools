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

goog.provide('registry.admin.Console');

goog.require('registry.Console');
goog.require('registry.admin.Registrar');
goog.require('registry.admin.Registry');



/**
 * The Admin Console.
 * @param {string} xsrfToken Populated by server-side soy template.
 * @constructor
 * @extends {registry.Console}
 * @final
 */
registry.admin.Console = function(xsrfToken) {
  registry.admin.Console.base(this, 'constructor', null);

  /** @type {string} */
  this.xsrfToken = xsrfToken;

  // XXX: Parent carries session but currently unused. Just using this class to
  //      get history, butter, etc.
  // XXX: This was in parent ctor but was triggering event dispatching before
  //      ready here.
  this.history.setEnabled(true);
};
goog.inherits(registry.admin.Console, registry.Console);


/** @override */
registry.admin.Console.prototype.handleHashChange = function() {
  var hashToken = this.history.getToken();
  var collection = hashToken.split('/');
  var id = null;
  // XXX: Should be generalized to deeper paths?
  if (collection.length == 2) {
    id = collection[1];
  }
  // Either the path is a registrar type, or default to registry.
  if (collection[0] == 'registrar') {
    new registry.admin.Registrar(
        this, this.xsrfToken, id).bindToDom(hashToken);
  } else {
    new registry.admin.Registry(
        this, this.xsrfToken, id).bindToDom(hashToken);
  }
};
