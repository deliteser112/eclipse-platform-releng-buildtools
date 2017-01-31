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

goog.provide('registry.testing');
goog.setTestOnly('registry.testing');

goog.require('goog.asserts');
goog.require('goog.dom');
goog.require('goog.dom.classlist');
goog.require('goog.dom.xml');
goog.require('goog.events.EventType');
goog.require('goog.format.JsonPrettyPrinter');
goog.require('goog.html.legacyconversions');
goog.require('goog.json');
goog.require('goog.testing.asserts');
goog.require('goog.testing.events');
goog.require('goog.testing.events.Event');
goog.require('goog.testing.net.XhrIo');


/**
 * Adds specified HTML string to document.
 * @param {string} html
 */
registry.testing.addToDocument = function(html) {
  goog.global.document.body.appendChild(
      goog.dom.safeHtmlToNode(
          goog.html.legacyconversions.safeHtmlFromString(html)));
};


/**
 * Extracts XML document from inside an {@code <iframe>}.
 * @param {string} xmlText
 * @return {!Document}
 */
registry.testing.loadXml = function(xmlText) {
  var xml = goog.dom.xml.loadXml(xmlText);
  goog.asserts.assert(xml != null);
  if ('parsererror' in xml) {
    fail(xml['parsererror']['keyValue']);
  }
  return xml;
};


/**
 * Extracts plain text string from inside an {@code <iframe>}.
 * @param {string|!Document|!Element} want
 * @param {string|!Document|!Element} got
 */
registry.testing.assertXmlEquals = function(want, got) {
  assertHTMLEquals(registry.testing.sanitizeXml_(want),
                   registry.testing.sanitizeXml_(got));
};


/**
 * Simulates a mouse click on a browser element.
 * @param {!Element} element
 */
registry.testing.click = function(element) {
  goog.testing.events.fireBrowserEvent(
      new goog.testing.events.Event(
          goog.events.EventType.CLICK, element));
};


/**
 * Asserts {@code element} has 'shown' class.
 * @param {!Element} element
 */
registry.testing.assertVisible = function(element) {
  assertTrue('Element should have CSS "shown" class',
             goog.dom.classlist.contains(element, 'shown'));
};


/**
 * Asserts {@code element} has 'hidden' class.
 * @param {!Element} element
 */
registry.testing.assertHidden = function(element) {
  assertTrue('Element should have CSS "hidden" class',
             goog.dom.classlist.contains(element, 'hidden'));
};


/**
 * Like {@code assertObjectEquals} but with a better error message.
 * @param {?Object} a
 * @param {?Object} b
 */
registry.testing.assertObjectEqualsPretty = function(a, b) {
  try {
    assertObjectEquals(a, b);
  } catch (e) {
    throw Error(e.message + '\n' +
        'expected: ' +
        registry.testing.pretty_.format(a) + '\n' +
        'got: ' +
        registry.testing.pretty_.format(b));
  }
};


/**
 * JSON request/response simulator for {@code ResourceComponent} subclasses.
 * @param {string} xsrfToken
 * @param {string} path server resource path.
 * @param {!Object} expectReqJson assert this object was sent,
 *     e.g. {'op':'read',{}}
 * @param {!Object} mockRspJson mock a response, e.g. {'set':[]}
 */
registry.testing.assertReqMockRsp =
    function(xsrfToken, path, expectReqJson, mockRspJson) {
  var xhr = goog.testing.net.XhrIo.getSendInstances().pop();
  assertTrue('XHR is active.', xhr.isActive());
  assertEquals(path, xhr.getLastUri());
  // XXX: XHR header checking should probably be added. Was inconsistent
  //      between admin and registrar consoles.
  registry.testing.assertObjectEqualsPretty(
      expectReqJson, goog.json.parse(xhr.getLastContent()));
  xhr.simulateResponse(200, goog.json.serialize(mockRspJson));
};


/**
 * Removes stuff from XML text that we don't want to compare.
 * @param {string|!Document|!Element} xml
 * @return {string}
 * @private
 */
registry.testing.sanitizeXml_ = function(xml) {
  var xmlString = goog.isString(xml) ? xml : goog.dom.xml.serialize(xml);
  return xmlString
      .replace(/^\s*<\?.*?\?>\s*/, '')       // Remove declaration thing.
      .replace(/xmlns(:\w+)?="[^"]+"/g, '')  // Remove namespace things.
      .replace(/>\s+</g, '><')               // Remove spaces between XML tags.
      .replace(/<!--.*?-->/, '');            // Remove comments.
};


/**
 * JSON pretty printer.
 * @type {!goog.format.JsonPrettyPrinter}
 * @private
 */
registry.testing.pretty_ = new goog.format.JsonPrettyPrinter(
    new goog.format.JsonPrettyPrinter.TextDelimiters());
