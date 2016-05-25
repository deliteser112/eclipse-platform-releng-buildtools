// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.model.domain.regtype;

import com.google.common.base.Splitter;

import google.registry.model.ImmutableObject;
import google.registry.model.eppinput.EppInput.CommandExtension;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;

/**
 * Base class for general domain commands with registration types (create, update, check, and
 * info).
 *
 * @see "https://gitlab.centralnic.com/centralnic/epp-registration-type-extension/tree/master"
 */
public class BaseRegTypeCommand extends ImmutableObject implements CommandExtension {

  /** The registration type (which may be a comma-delimited list of values). */
  @XmlElement(name = "type")
  String type;

  public List<String> getRegistrationTypes() {
    return Splitter.on(',').splitToList(type);
  }
}

