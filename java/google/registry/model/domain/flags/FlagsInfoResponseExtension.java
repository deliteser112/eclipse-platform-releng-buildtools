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

package google.registry.model.domain.flags;

import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A flags extension that may be present on domain info responses. See {@link
 * FlagsCreateResponseExtension} for more details about the flags extension. Info commands sent by
 * the registrar do not specify the flags extension, but TLDs which use flags to support specific
 * functionality can pass them back to the registrar in info responses to indicate the current state
 * of a domain.
 */
@XmlRootElement(name = "infData")
public class FlagsInfoResponseExtension implements ResponseExtension {
  @XmlElement(name = "flag")
  List<String> flags;
}
