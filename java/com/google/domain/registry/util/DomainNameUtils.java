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

package com.google.domain.registry.util;

import com.google.common.base.Ascii;
import com.google.common.net.InternetDomainName;

/** Utility methods related to domain names. */
public final class DomainNameUtils {

  /** Prefix for unicode domain name parts. */
  public static final String ACE_PREFIX = "xn--";
  public static final String ACE_PREFIX_REGEX = "^xn--";

  /** Checks whether "name" is a strict subdomain of "potentialParent". */
  public static boolean isUnder(InternetDomainName name, InternetDomainName potentialParent) {
    int numNameParts = name.parts().size();
    int numParentParts = potentialParent.parts().size();
    return numNameParts > numParentParts
        && name.parts().subList(numNameParts - numParentParts, numNameParts)
            .equals(potentialParent.parts());
  }

  /** Canonicalizes a domain name by lowercasing and converting unicode to punycode. */
  public static String canonicalizeDomainName(String label) {
    return Idn.toASCII(Ascii.toLowerCase(label));
  }

  private DomainNameUtils() {}
}
