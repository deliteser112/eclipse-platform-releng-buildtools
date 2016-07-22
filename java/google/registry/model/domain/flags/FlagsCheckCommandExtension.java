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

import google.registry.model.eppinput.EppInput.CommandExtension;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A flags extension that may be present on domain check commands. The extension will specify a
 * map from domain to a set of flags to be applied to any checks performed on that domain. So if
 * the client wants to know how much a create would cost on a particular domain with flag X set,
 * they can send a check command with a flags extension that associates the domain with flag X.
 * See {@FlagsCreateCommandExtension} for more information about the flags extension.
 */
@XmlRootElement(name = "check")
public class FlagsCheckCommandExtension implements CommandExtension {
  @XmlElement(name = "domain")
  List<FlagsCheckCommandExtensionItem> domains;
}
