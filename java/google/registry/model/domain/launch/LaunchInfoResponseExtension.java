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

package google.registry.model.domain.launch;

import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.annotation.Embed;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.model.mark.Mark;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * An XML data object that represents a launch extension that may be present on the response to EPP
 * domain application info commands.
 */
@Embed
@XmlRootElement(name = "infData")
@XmlType(propOrder = { "phase", "applicationId", "applicationStatus", "marks"})
public class LaunchInfoResponseExtension extends LaunchExtension implements ResponseExtension {

  /** The current status of this application. */
  @XmlElement(name = "status")
  ApplicationStatus applicationStatus;

  /** The marks associated with this application. */
  @XmlElement(name = "mark", namespace = "urn:ietf:params:xml:ns:mark-1.0")
  List<Mark> marks;

  /** Builder for {@link LaunchInfoResponseExtension}. */
  public static class Builder
      extends LaunchExtension.Builder<LaunchInfoResponseExtension, Builder> {
    public Builder setApplicationStatus(ApplicationStatus applicationStatus) {
      getInstance().applicationStatus = applicationStatus;
      return this;
    }

    public Builder setMarks(ImmutableList<Mark> marks) {
      getInstance().marks = marks;
      return this;
    }
  }
}
