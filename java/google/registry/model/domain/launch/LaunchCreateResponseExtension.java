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

package google.registry.model.domain.launch;

import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * An XML data object that represents a launch extension that may be present on the response to EPP
 * domain application create commands.
 */
@XmlRootElement(name = "creData")
@XmlType(propOrder = {"phase", "applicationId"})
public class LaunchCreateResponseExtension extends LaunchExtension implements ResponseExtension {
  /** Builder for {@link LaunchCreateResponseExtension}. */
  public static class Builder
      extends LaunchExtension.Builder<LaunchCreateResponseExtension, Builder> {}
}
