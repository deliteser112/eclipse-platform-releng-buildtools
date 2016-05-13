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

package com.google.domain.registry.model.domain.fee;

import static com.google.domain.registry.util.CollectionUtils.nullToEmptyImmutableCopy;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.domain.Period;
import com.google.domain.registry.model.eppinput.EppInput.CommandExtension;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/** A fee extension that may be present on domain check commands. */
@XmlRootElement(name = "check")
public class FeeCheckExtension extends ImmutableObject implements CommandExtension {

  /** The default validity period (if not specified) is 1 year for all operations. */
  static final Period DEFAULT_PERIOD = Period.create(1, Period.Unit.YEARS);

  @XmlElement(name = "domain")
  Set<DomainCheck> domains;

  public ImmutableSet<DomainCheck> getDomains() {
    return nullToEmptyImmutableCopy(domains);
  }

  /** A check request for the fee to perform a given command on a given domain. */
  @XmlType(propOrder = {"name", "currency", "command", "period"})
  public static class DomainCheck extends BaseFeeRequest {
    /** The fully qualified domain name being checked. */
    String name;

    public String getName() {
      return name;
    }
  }
}
