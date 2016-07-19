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

package google.registry.model.domain.fee06;

import google.registry.model.domain.fee.FeeTransformResponseExtensionImplNoCredits;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * An XML data object that represents a fee extension that may be present on the response to EPP
 * domain update commands.
 */
@XmlRootElement(name = "updData")
@XmlType(propOrder = {"currency", "fees"})
public class FeeUpdateResponseExtensionV06 extends FeeTransformResponseExtensionImplNoCredits {
  /** A builder for {@link FeeUpdateResponseExtensionV06}. */
  public static class Builder
      extends FeeTransformResponseExtensionImplNoCredits
          .Builder<FeeUpdateResponseExtensionV06, Builder> {}
}
