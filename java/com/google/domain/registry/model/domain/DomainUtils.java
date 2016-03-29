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

package com.google.domain.registry.model.domain;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.net.InternetDomainName;

/**
 * Utility class for dealing with domains.
 */
public final class DomainUtils {

  /**
   * Returns the canonicalized TLD part of a valid domain name (just an SLD, no subdomains) by
   * stripping off the leftmost part.
   *
   * <p>This function is compatible with multi-part tlds, e.g. {@code co.uk}. This function will
   * also work on domains for which the registry is not authoritative. If you are certain that the
   * input will be under a TLD this registry controls, then it is preferable to use
   * {@link com.google.domain.registry.model.registry.Registries#findTldForName(InternetDomainName)
   * Registries#findTldForName}, which will work on hostnames in addition to domains.
   *
   * @param fullyQualifiedDomainName must be a punycode SLD (not a host or unicode)
   * @throws IllegalArgumentException if there is no TLD
   */
  public static String getTldFromDomainName(String fullyQualifiedDomainName) {
    checkArgument(
        !Strings.isNullOrEmpty(fullyQualifiedDomainName),
        "fullyQualifiedDomainName cannot be null or empty");
    InternetDomainName domainName = InternetDomainName.from(fullyQualifiedDomainName);
    checkArgument(domainName.hasParent(), "fullyQualifiedDomainName does not have a TLD");
    return domainName.parent().toString();
  }

  private DomainUtils() {}
}
