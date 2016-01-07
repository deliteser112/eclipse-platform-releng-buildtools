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

goog.provide('registry.Component');

goog.require('goog.dom');
goog.require('goog.dom.TagName');
goog.require('goog.dom.classlist');
goog.require('goog.events');
goog.require('goog.events.EventHandler');
goog.require('goog.events.EventType');
goog.require('registry.soy.forms');
goog.require('registry.util');

goog.forwardDeclare('registry.Console');



/**
 * Base component for UI.
 *
 * <pre>
 * ui/js/component.js - Base UI class.
 *       ^
 *       |
 *       edit_item.js - Common controls for editable items.
 *       ^
 *        \
 *         |-ui/js/resource_component.js - JSON resources
 *         |       ^
 *         |        \
 *         |         |- ui/js/registrar/settings.js
 *         |
 *         |-ui/js/registrar/xml_resource_component.js - EPP resources
 *                           ^
 *                            \
 *                            |- ui/js/registrar/contact.js
 *                            |- ui/js/registrar/domain.js
 *                            |- ui/js/registrar/host.js
 * </pre>
 *
 * @param {!registry.Console} cons the console singleton.
 * @constructor
 * @extends {goog.events.EventHandler}
 */
registry.Component = function(cons) {
  registry.Component.base(this, 'constructor');

  /** @type {!registry.Console} */
  this.console = cons;

  /**
   * The hashPath this component is mapped by.  This is set by the
   * console after construction.
   * @type {string}
   */
  this.basePath = '';

  /**
   * Bean counter that's used by {@code addRemBtnHandlers},
   * e.g. {@code typeCounts['host']++} when user adds or removes.
   * @type {!Object.<string, number>}
   * @protected
   */
  this.typeCounts = {};

  /**
   * Stateful UI/server session model.
   * @type {?Object.<string, ?>}
   */
  this.model = null;
};
goog.inherits(registry.Component, goog.events.EventHandler);


/**
 * Subclasses shold override this to implement panel display.
 * @param {string} id The target resource id.
 */
registry.Component.prototype.bindToDom = function(id) {
  registry.util.unbutter();
};


// XXX: Should clean up the many remove button handler setup funcs.
/**
 * Shared functionality for contact and host add button click events
 * to add form elements for inputing a new item name.  Requires the
 * template to have:
 *
 * <ul>
 * <li>an add button with id="domain-[type]-add-button".
 * <li>a table row with id="domain-[type]s-footer".
 * </ul>
 *
 * @param {string} type e.g. 'contact', 'host'.
 * @param {function(): string} newFieldNameFn generates new form field's name.
 * @param {function(string): (?Element)=} opt_onAddFn currying further setup.
 * @param {function()=} opt_tmpl input element template.
 * @param {!Object=} opt_tmplArgs input element template parameter object.
 * @param {boolean=} opt_disable optionally disable the add button.
 * @protected
 */
registry.Component.prototype.addRemBtnHandlers = function(
    type,
    newFieldNameFn,
    opt_onAddFn,
    opt_tmpl,
    opt_tmplArgs,
    opt_disable) {
  var addBtnId = 'domain-' + type + '-add-button';
  var addBtn = goog.dom.getRequiredElement(addBtnId);
  if (!opt_disable) {
    addBtn.removeAttribute('disabled');
  }
  var addButtonClickCallback = function() {
    var fieldElts = [];
    var newFieldName = newFieldNameFn();
    var tmpl =
        opt_tmpl ? opt_tmpl : registry.soy.forms.inputFieldRow;
    var tmplArgs = opt_tmplArgs ? opt_tmplArgs : {
      label: 'New ' + type,
      name: newFieldName + '.value'
    };
    var newFieldInputRow = registry.util.renderBeforeRow(
        'domain-' + type + 's-footer', tmpl, tmplArgs);
    fieldElts.push(newFieldInputRow);
    // Save the add/rem op type as a hidden elt for use by
    // determine EPP add/remove semantics in subclasses.
    // e.g. domain.js#saveItem()
    var opElt = goog.dom.createDom(goog.dom.TagName.INPUT, {
      'type': 'hidden',
      'name': newFieldName + '.op',
      'value': 'add'
    });
    newFieldInputRow.lastChild.appendChild(opElt);
    if (opt_onAddFn) {
      var elt = opt_onAddFn(newFieldName);
      if (elt) {
        fieldElts.push(elt);
      }
    }
    this.appendRemoveBtn(
        goog.dom.getLastElementChild(newFieldInputRow),
        fieldElts,
        goog.bind(function() {
          this.typeCounts[type]--;
        }, this));
    this.typeCounts[type]++;
  };
  goog.events.listen(goog.dom.getRequiredElement(addBtnId),
                     goog.events.EventType.CLICK,
                     goog.bind(addButtonClickCallback, this));
};


/**
 * Helper for making an element removable.
 * @param {Element} parent The element to append the remove button to.
 * @param {(Array.<Element>|function())=} opt_eltsToRemove
 *     Elements to remove when the button is clicked or Function to do
 *     the removing for full customization.
 * @param {function()=} opt_cb callback will be called if no
 *     customized function is given.
 */
registry.Component.prototype.appendRemoveBtn =
    function(parent, opt_eltsToRemove, opt_cb) {
  var rmBtn = goog.dom.createDom(goog.dom.TagName.BUTTON,
                                 goog.getCssName('kd-button'),
                                 'Remove');
  goog.dom.appendChild(parent, rmBtn);
  var clickCb;
  if (opt_eltsToRemove instanceof Function) {
    clickCb = opt_eltsToRemove;
  } else {
    var eltsToRemove = opt_eltsToRemove ? opt_eltsToRemove : [parent];
    clickCb = function() {
      for (var i = 0; i < eltsToRemove.length; i++) {
        goog.dom.removeNode(eltsToRemove[i]);
      }
      if (opt_cb) {
        opt_cb();
      }
    };
  }
  goog.events.listen(rmBtn, goog.events.EventType.CLICK, clickCb, true);
};


/**
 * Bind the remove button action for the given container element.
 * @param {!Element} containerElt
 */
registry.Component.prototype.enableRemoveButton = function(containerElt) {
  var rmBtn = goog.dom.getElementByClass(
      goog.getCssName('remove'), containerElt);
  goog.dom.classlist.toggle(rmBtn, goog.getCssName('hidden'));
  goog.events.listen(rmBtn, goog.events.EventType.CLICK, function() {
    goog.dom.removeNode(goog.dom.getParentElement(rmBtn));
  });
};
