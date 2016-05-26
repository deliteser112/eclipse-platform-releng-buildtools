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

import com.google.common.base.Joiner;

import google.registry.model.eppoutput.Response.ResponseExtension;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * An XML data object that represents a registration type extension that may be present on the
 * response to EPP domain info EPP commands.
 */
@XmlRootElement(name = "infData")
public class RegTypeInfoResponseExtension extends BaseRegTypeCommand implements ResponseExtension {

  public static RegTypeInfoResponseExtension create(List<String> registrationTypes) {
    RegTypeInfoResponseExtension instance = new RegTypeInfoResponseExtension();
    instance.regType = Joiner.on(",").join(registrationTypes);
    return instance;
  }

  private RegTypeInfoResponseExtension() {}
}
