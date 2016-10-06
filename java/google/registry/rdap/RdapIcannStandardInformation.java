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

package google.registry.rdap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * This file contains boilerplate required by the ICANN RDAP Profile.
 *
 * @see "https://www.icann.org/resources/pages/rdap-operational-profile-2016-07-26-en"
 */

public class RdapIcannStandardInformation {

  /** Required by ICANN RDAP Profile section 1.4.10. */
  private static final ImmutableMap<String, Object> CONFORMANCE_REMARK =
      ImmutableMap.<String, Object>of(
          "description",
          ImmutableList.of(
              "This response conforms to the RDAP Operational Profile for gTLD Registries and"
                  + " Registrars version 1.0"));

  /** Required by ICANN RDAP Profile section 1.5.18. */
  private static final ImmutableMap<String, Object> DOMAIN_STATUS_CODES_REMARK =
      ImmutableMap.<String, Object> of(
          "title",
          "EPP Status Codes",
          "description",
          ImmutableList.of(
              "For more information on domain status codes, please visit https://icann.org/epp"),
          "links",
          ImmutableList.of(
              ImmutableMap.of(
                  "value", "https://icann.org/epp",
                  "rel", "alternate",
                  "href", "https://icann.org/epp",
                  "type", "text/html")));

  /** Required by ICANN RDAP Profile section 1.5.20. */
  private static final ImmutableMap<String, Object> INACCURACY_COMPLAINT_FORM_REMARK =
      ImmutableMap.<String, Object> of(
          "description",
          ImmutableList.of(
              "URL of the ICANN Whois Inaccuracy Complaint Form: https://www.icann.org/wicf"),
          "links",
          ImmutableList.of(
              ImmutableMap.of(
                  "value", "https://www.icann.org/wicf",
                  "rel", "alternate",
                  "href", "https://www.icann.org/wicf",
                  "type", "text/html")));

  /** Boilerplate remarks required by domain responses. */
  static final ImmutableList<ImmutableMap<String, Object>> domainBoilerplateRemarks =
      ImmutableList.of(
          CONFORMANCE_REMARK, DOMAIN_STATUS_CODES_REMARK, INACCURACY_COMPLAINT_FORM_REMARK);

  /** Boilerplate remarks required by nameserver and entity responses. */
  static final ImmutableList<ImmutableMap<String, Object>> nameserverAndEntityBoilerplateRemarks =
      ImmutableList.of(CONFORMANCE_REMARK);

  /** Required by ICANN RDAP Profile section 1.4.8 / 1.4.9, as interpreted. */
  static final ImmutableMap<String, Object> SUMMARY_DATA_REMARK =
      ImmutableMap.<String, Object> of(
          "title",
          "Incomplete Data",
          "description",
          ImmutableList.of(
              "Summary data only. For complete data, send a specific query for the object."),
          "type",
          "object truncated due to unexplainable reasons");


  /** Required by ICANN RDAP Profile section 1.4.8 / 1.4.9, as interpreted. */
  static final ImmutableMap<String, Object> TRUNCATED_RESULT_SET_NOTICE =
      ImmutableMap.<String, Object> of(
          "title",
          "Search Policy",
          "description",
          ImmutableList.of("Search results per query are limited."),
          "type",
          "result set truncated due to unexplainable reasons");
  
  /** Truncation notice as a singleton list, for easy use. */
  static final ImmutableList<ImmutableMap<String, Object>> TRUNCATION_NOTICES =
      ImmutableList.of(TRUNCATED_RESULT_SET_NOTICE);
}

