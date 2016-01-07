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

goog.provide('registry.Resource');

goog.require('goog.json');
goog.require('registry.Session');

goog.forwardDeclare('goog.Uri');



/**
 * Provide a CRUD view of a server resource.
 *
 * @param {!goog.Uri} baseUri Target RESTful resource.
 * @param {string} xsrfToken Security token to pass back to the server.
 * @extends {registry.Session}
 * @constructor
 */
registry.Resource = function(baseUri, xsrfToken) {
  registry.Resource.base(this, 'constructor', baseUri, xsrfToken,
                         registry.Session.ContentType.JSON);
};
goog.inherits(registry.Resource, registry.Session);


/**
 * Get the resource from the server.
 *
 * @param {!Object} args Params for server. Do not set the 'op' field on args.
 * @param {!Function} callback For retrieved resource.
 */
registry.Resource.prototype.read = function(args, callback) {
  this.send_('read', args, callback);
};


/**
 * Create the resource on the server.
 *
 * @param {!Object} args params for server. Do not set the 'op' field on args.
 * @param {!Function} callback on success.
 * @param {string} newId name for the new resource.
 * @throws {!Exception} if the 'op' field is set on args.
 */
registry.Resource.prototype.create = function(args, callback, newId) {
  this.send_('create', args, callback, newId);
};


/**
 * Create the resource on the server.
 *
 * @param {!Object} args params for server. Do not set the 'op' field on args.
 * @param {!Function} callback on success.
 * @throws {!Exception} if the 'op' field is set on args.
 */
registry.Resource.prototype.update = function(args, callback) {
  this.send_('update', args, callback);
};


/**
 * RESTful access to resources on the server.
 *
 * @param {string} opCode One of (create|read|update)
 * @param {!Object} argsObj arguments for the operation.
 * @param {!Function} callback For XhrIo result throws.
 * @param {string=} opt_newId name for the new resource.
 * @private
 */
registry.Resource.prototype.send_ =
    function(opCode, argsObj, callback, opt_newId) {
  // NB: must be declared this way in order to avoid compiler renaming
  var req = {};
  req['op'] = opCode;
  req['args'] = argsObj;
  if (opt_newId) {
    this.uri.setPath(this.uri.getPath() + '/' + opt_newId);
  }
  this.sendXhrIo(goog.json.serialize(req), callback);
};
