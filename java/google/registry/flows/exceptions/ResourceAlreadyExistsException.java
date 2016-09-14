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

package google.registry.flows.exceptions;

import com.google.common.annotations.VisibleForTesting;
import google.registry.flows.EppException.ObjectAlreadyExistsException;

/** Resource with this id already exists. */
public class ResourceAlreadyExistsException extends ObjectAlreadyExistsException {

  /** Whether this was thrown from a "failfast" context. Useful for testing. */
  final boolean failfast;

  public ResourceAlreadyExistsException(String resourceId, boolean failfast) {
    super(String.format("Object with given ID (%s) already exists", resourceId));
    this.failfast = failfast;
  }

  public ResourceAlreadyExistsException(String resourceId) {
    this(resourceId, false);
  }

  @VisibleForTesting
  public boolean isFailfast() {
    return failfast;
  }
}
