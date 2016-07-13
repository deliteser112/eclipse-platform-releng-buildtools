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
 * A flags extension that may be present on domain create commands. The extension specifies one or
 * more flag strings. The extension does not dictate the use of any specific flags, but leaves it up
 * to the particular TLD-specific logic. Some TLDs have special rules regarding discounts and
 * eligibility. Such TLDs can define whatever flags they need to use for interacting with the
 * registrar, and pass them using the flags extension.
 */
@XmlRootElement(name = "create")
public class FlagsCreateCommandExtension implements CommandExtension {
  @XmlElement(name = "flag")
  List<String> flags;

  public List<String> getFlags() {
    return flags;
  }
}
