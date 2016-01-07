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

package google.registry.flows.domain.flags;

import google.registry.flows.EppException.RequiredParameterMissingException;

/** Required extension flag missing. */
public class ExtensionFlagMissingException extends RequiredParameterMissingException {
  public ExtensionFlagMissingException(String flag) {
    super(String.format("Flag %s must be specified", flag));
  }

  public ExtensionFlagMissingException(String flag1, String flag2) {
    super(String.format("Either %s or %s must be specified", flag1, flag2));
  }
}
