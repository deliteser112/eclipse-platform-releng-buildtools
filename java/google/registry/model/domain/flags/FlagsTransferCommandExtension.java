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

package google.registry.model.domain.flags;

import google.registry.model.eppinput.EppInput.CommandExtension;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * A flags extension that may be present on domain transfer commands. See {@link
 * FlagsCreateResponseExtension} for more details about the flags extension. For TLDs which require
 * flags to support special functionality, some flags may need to be modified as part of the
 * transfer process. In such a case, the extension looks the same as it would for an equivalent
 * {@link FlagsUpdateCommandExtension} command.
 */
@XmlRootElement(name = "transfer")
@XmlType(propOrder = {"add", "rem"})
public class FlagsTransferCommandExtension implements CommandExtension {
  FlagsList add; // list of flags to be added (turned on)
  FlagsList rem; // list of flags to be removed (turned off)

  public FlagsList getAddFlags() {
    return add;
  }

  public FlagsList getRemoveFlags() {
    return rem;
  }
}
