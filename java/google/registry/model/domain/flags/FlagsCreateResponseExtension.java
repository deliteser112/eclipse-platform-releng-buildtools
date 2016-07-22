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
 * A flags extension that may be present on domain create responses. The extension specifies one or
 * more flag strings. See {@link FlagsCreateResponseExtension} for more details about the flags
 * extension. The server may return different flags in the create response than were passed in the
 * create command, though often they are likely to be the same.
 */
@XmlRootElement(name = "creData")
public class FlagsCreateResponseExtension implements ResponseExtension {
  @XmlElement(name = "flag")
  List<String> flags;
}
