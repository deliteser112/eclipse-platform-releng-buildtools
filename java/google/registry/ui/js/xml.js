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

goog.provide('registry.xml');
goog.provide('registry.xml.XmlJson');

goog.require('goog.dom.NodeType');
goog.require('goog.object');


/**
 * Turns XML document into a JSON data structure. This function is similar to
 * <a href="https://developer.mozilla.org/en-US/docs/JXON">Mozilla JXON</a>
 * except it handles text differently. This routine will not coalesce text
 * interspersed with elements. This routine does not coerce string types to
 * number, boolean, or null.
 * @param {!Node} node
 * @return {!Object<string, registry.xml.XmlJson>}
 * @throws {Error} upon encountering interspersed text.
 */
registry.xml.convertToJson = function(node) {
  var result = goog.object.create();
  if (goog.isDefAndNotNull(node.attributes)) {
    for (var i = 0; i < node.attributes.length; i++) {
      var attr = node.attributes.item(i);
      goog.object.set(result, '@' + attr.name, attr.value || '');
    }
  }
  for (var j = 0; j < node.childNodes.length; j++) {
    var child = node.childNodes.item(j);
    switch (child.nodeType) {
      case goog.dom.NodeType.TEXT:
      case goog.dom.NodeType.CDATA_SECTION:
        var text = String(child.nodeValue).trim();
        if (text != '') {
          var curr = goog.object.get(result, registry.xml.jsonValueFieldName_);
          if (goog.isDef(curr)) {
            throw new Error(
                'XML text "' + curr + '" interspersed with "' + text + '"');
          }
          goog.object.set(result, registry.xml.jsonValueFieldName_, text);
        }
        break;
      case goog.dom.NodeType.ELEMENT:
        var json = registry.xml.convertToJson(child);
        var name = child.nodeName;
        if (goog.object.containsKey(result, name)) {
          var field = goog.object.get(result, name);
          if (goog.isArray(field)) {
            field.push(json);
          } else {
            goog.object.set(result, name, [field, json]);
          }
        } else {
          goog.object.set(result, name, json);
        }
        break;
    }
  }
  return result;
};


/**
 * XML JSON object recursive type definition.
 * @typedef {(string|
 *            !Array<registry.xml.XmlJson>|
 *            !Object<string, registry.xml.XmlJson>)}
 */
registry.xml.XmlJson;


/**
 * XML JSON value field name, inherited from JXON.
 * @private {string}
 * @const
 */
registry.xml.jsonValueFieldName_ = 'keyValue';
