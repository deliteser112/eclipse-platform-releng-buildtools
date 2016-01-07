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

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * An single domain item in a domain check command flags extension. Each item associates a single
 * domain with one or more flags. This object appears as part of a list contained in {@link
 * FlagsCheckCommandExtension}.
 */
@XmlType(propOrder = {"name", "flags"})
public class FlagsCheckCommandExtensionItem {
  String name;

  @XmlElement(name = "flag")
  List<String> flags;
}
