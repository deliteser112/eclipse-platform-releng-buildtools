// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.model.domain.fee;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.eppoutput.Response.ResponseExtension;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * An XML data object that represents a fee extension that may be present on the response to EPP
 * domain check commands.
 */
@XmlRootElement(name = "chkData")
public class FeeCheckResponseExtension extends ImmutableObject implements ResponseExtension {

  /** Check responses. */
  @XmlElement(name = "cd")
  ImmutableList<FeeCheck> feeChecks;

  @VisibleForTesting
  public ImmutableList<FeeCheck> getChecks() {
    return feeChecks;
  }

  public static FeeCheckResponseExtension create(ImmutableList<FeeCheck> feeChecks) {
    FeeCheckResponseExtension instance = new FeeCheckResponseExtension();
    instance.feeChecks = feeChecks;
    return instance;
  }

  /** The response for a check on a single resource. */
  @XmlType(propOrder = {"name", "currency", "command", "period", "fee", "feeClass"})
  public static class FeeCheck extends BaseFeeResponse {
    /** The name of the domain that was checked, with an attribute indicating if it is premium. */
    String name;

    /** A builder for {@link FeeCheck}. */
    public static class Builder extends BaseFeeResponse.Builder<FeeCheck, Builder> {
      public Builder setName(String name) {
        getInstance().name = name;
        return this;
      }
    }
  }
}
