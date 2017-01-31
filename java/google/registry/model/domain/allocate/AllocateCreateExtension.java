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

package google.registry.model.domain.allocate;

import google.registry.model.ImmutableObject;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.eppinput.EppInput.CommandExtension;
import javax.xml.bind.annotation.XmlRootElement;
import org.joda.time.DateTime;

/**
 * An XML data object that represents an allocate extension that will be present on EPP commands to
 * allocate a domain from an application.
 *
 * <p>This object holds XML data which JAXB will unmarshal from an EPP domain create command
 * extension.  The XML will have the following enclosing structure:
 *
 * <pre> {@code
 *   <epp>
 *     <command>
 *       <create>
 *         <!-- domain create XML data -->
 *       </create>
 *       <extension>
 *         <allocate:create>
 *           <!-- allocate create XML payload data -->
 *         </allocate:create>
 *       </extension>
 *     </command>
 *   </epp>
 * } </pre>
 *
 * @see CommandExtension
 */
@XmlRootElement(name = "create")
public class AllocateCreateExtension extends ImmutableObject implements CommandExtension {

  /** Holds the ROID of the application that was used to allocate this domain. */
  String applicationRoid;

  /** The time that the application was created. */
  DateTime applicationTime;

  /**
   * Signed mark identifier for this create. Only present when allocating a domain from a sunrise
   * application.
   */
  String smdId;

  /**
   * The claims notice for this create. Only present when allocating a domain from a landrush
   * application that matches a pre-registered mark in the TMCH.
   */
  LaunchNotice notice;

  public String getApplicationRoid() {
    return applicationRoid;
  }

  public DateTime getApplicationTime() {
    return applicationTime;
  }

  public String getSmdId() {
    return smdId;
  }

  public LaunchNotice getNotice() {
    return notice;
  }
}
