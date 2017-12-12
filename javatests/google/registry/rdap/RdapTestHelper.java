// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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
import google.registry.config.RdapNoticeDescriptor;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

public class RdapTestHelper {

  enum ContactNoticeType {
    NONE,
    DOMAIN,
    CONTACT
  }

  static ImmutableMap.Builder<String, Object> getBuilderExcluding(
      Map<String, Object> map, Set<String> keysToExclude) {
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    for (Entry<String, Object> entry : map.entrySet()) {
      if (!keysToExclude.contains(entry.getKey())) {
        builder.put(entry);
      }
    }
    return builder;
  }

  static void addNotices(
      ImmutableMap.Builder<String, Object> builder, String linkBase) {
    addNotices(builder, linkBase, ContactNoticeType.NONE, null);
  }

  static void addNotices(
      ImmutableMap.Builder<String, Object> builder,
      String linkBase,
      ContactNoticeType contactNoticeType,
      @Nullable Object otherNotices) {
    ImmutableList.Builder<ImmutableMap<String, Object>> noticesBuilder =
        getBuilderWithOthersAdded(otherNotices);
    switch (contactNoticeType) {
      case DOMAIN:
        noticesBuilder.add(
            ImmutableMap.of(
                "title", "Contacts Hidden",
                "description",
                    ImmutableList.of("Domain contacts are visible only to the owning registrar."),
                "type", "object truncated due to unexplainable reasons"));
        break;
      case CONTACT:
        noticesBuilder.add(
            ImmutableMap.of(
                "title", "Contact Personal Data Hidden",
                "description",
                    ImmutableList.of(
                        "Contact personal data is visible only to the owning registrar."),
                "type", "object truncated due to unexplainable reasons"));
        break;
      default:
        break;
    }
    noticesBuilder.add(
        ImmutableMap.of(
            "title", "RDAP Terms of Service",
            "description",
                ImmutableList.of(
                    "By querying our Domain Database, you are agreeing to comply with these terms"
                        + " so please read them carefully.",
                    "Any information provided is 'as is' without any guarantee of accuracy.",
                    "Please do not misuse the Domain Database. It is intended solely for"
                        + " query-based access.",
                    "Don't use the Domain Database to allow, enable, or otherwise support the"
                        + " transmission of mass unsolicited, commercial advertising or"
                        + " solicitations.",
                    "Don't access our Domain Database through the use of high volume, automated"
                        + " electronic processes that send queries or data to the systems of any"
                        + " ICANN-accredited registrar.",
                    "You may only use the information contained in the Domain Database for lawful"
                        + " purposes.",
                    "Do not compile, repackage, disseminate, or otherwise use the information"
                        + " contained in the Domain Database in its entirety, or in any substantial"
                        + " portion, without our prior written permission.",
                    "We may retain certain details about queries to our Domain Database for the"
                        + " purposes of detecting and preventing misuse.",
                    "We reserve the right to restrict or deny your access to the database if we"
                        + " suspect that you have failed to comply with these terms.",
                    "We reserve the right to modify this agreement at any time."),
            "links",
                ImmutableList.of(
                    ImmutableMap.of(
                        "value", linkBase + "help/tos",
                        "rel", "alternate",
                        "href", "https://www.registry.tld/about/rdap/tos.html",
                        "type", "text/html"))));
    builder.put("notices", noticesBuilder.build());
  }

  static void addNonDomainBoilerplateRemarks(ImmutableMap.Builder<String, Object> builder) {
    addNonDomainBoilerplateRemarks(builder, null);
  }

  static void addNonDomainBoilerplateRemarks(
      ImmutableMap.Builder<String, Object> builder, @Nullable Object otherRemarks) {
    ImmutableList.Builder<ImmutableMap<String, Object>> remarksBuilder =
        getBuilderWithOthersAdded(otherRemarks);
    remarksBuilder.add(
        ImmutableMap.of(
            "description",
            ImmutableList.of(
                "This response conforms to the RDAP Operational Profile for gTLD Registries and"
                    + " Registrars version 1.0")));
    builder.put("remarks", remarksBuilder.build());
  }

  static void addDomainBoilerplateRemarks(ImmutableMap.Builder<String, Object> builder) {
    addDomainBoilerplateRemarks(builder, false, null);
  }

  static void addDomainBoilerplateRemarks(
      ImmutableMap.Builder<String, Object> builder,
      boolean addNoContactRemark,
      @Nullable Object otherRemarks) {
    ImmutableList.Builder<ImmutableMap<String, Object>> remarksBuilder =
        getBuilderWithOthersAdded(otherRemarks);
    if (addNoContactRemark) {
      remarksBuilder.add(
          ImmutableMap.of(
              "title", "Contacts Hidden",
              "description",
                  ImmutableList.of("Domain contacts are visible only to the owning registrar."),
              "type", "object truncated due to unexplainable reasons"));
    }
    remarksBuilder.add(
        ImmutableMap.of(
            "description",
            ImmutableList.of(
                "This response conforms to the RDAP Operational Profile for gTLD Registries and"
                    + " Registrars version 1.0")));
    remarksBuilder.add(
        ImmutableMap.of(
            "title",
            "EPP Status Codes",
            "description",
            ImmutableList.of(
                "For more information on domain status codes, please visit"
                    + " https://icann.org/epp"),
            "links",
            ImmutableList.of(
                ImmutableMap.of(
                    "value", "https://icann.org/epp",
                    "rel", "alternate",
                    "href", "https://icann.org/epp",
                    "type", "text/html"))));
    remarksBuilder.add(
        ImmutableMap.of(
            "description",
            ImmutableList.of(
                "URL of the ICANN Whois Inaccuracy Complaint Form: https://www.icann.org/wicf"),
            "links",
            ImmutableList.of(
                ImmutableMap.of(
                    "value", "https://www.icann.org/wicf",
                    "rel", "alternate",
                    "href", "https://www.icann.org/wicf",
                    "type", "text/html"))));
    builder.put("remarks", remarksBuilder.build());
  }

  private static ImmutableList.Builder<ImmutableMap<String, Object>> getBuilderWithOthersAdded(
      @Nullable Object others) {
    ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
    if ((others != null) && (others instanceof ImmutableList<?>)) {
      @SuppressWarnings("unchecked")
      ImmutableList<ImmutableMap<String, Object>> othersList =
          (ImmutableList<ImmutableMap<String, Object>>) others;
      builder.addAll(othersList);
    }
    return builder;
  }

  static RdapJsonFormatter getTestRdapJsonFormatter() {
    RdapJsonFormatter rdapJsonFormatter = new RdapJsonFormatter();
    rdapJsonFormatter.rdapTosPath = "/tos";
    rdapJsonFormatter.rdapHelpMap = ImmutableMap.of(
        "/",
        RdapNoticeDescriptor.builder()
            .setTitle("RDAP Help")
            .setDescription(ImmutableList.of(
                "domain/XXXX",
                "nameserver/XXXX",
                "entity/XXXX",
                "domains?name=XXXX",
                "domains?nsLdhName=XXXX",
                "domains?nsIp=XXXX",
                "nameservers?name=XXXX",
                "nameservers?ip=XXXX",
                "entities?fn=XXXX",
                "entities?handle=XXXX",
                "help/XXXX"))
            .setLinkValueSuffix("help/")
            .setLinkHrefUrlString("https://github.com/google/nomulus/blob/master/docs/rdap.md")
            .build(),
        "/tos",
        RdapNoticeDescriptor.builder()
            .setTitle("RDAP Terms of Service")
            .setDescription(ImmutableList.of(
                "By querying our Domain Database, you are agreeing to comply with these terms so"
                    + " please read them carefully.",
                "Any information provided is 'as is' without any guarantee of accuracy.",
                "Please do not misuse the Domain Database. It is intended solely for"
                    + " query-based access.",
                "Don't use the Domain Database to allow, enable, or otherwise support the"
                    + " transmission of mass unsolicited, commercial advertising or"
                    + " solicitations.",
                "Don't access our Domain Database through the use of high volume, automated"
                    + " electronic processes that send queries or data to the systems of any"
                    + " ICANN-accredited registrar.",
                "You may only use the information contained in the Domain Database for lawful"
                    + " purposes.",
                "Do not compile, repackage, disseminate, or otherwise use the information"
                    + " contained in the Domain Database in its entirety, or in any substantial"
                    + " portion, without our prior written permission.",
                "We may retain certain details about queries to our Domain Database for the"
                    + " purposes of detecting and preventing misuse.",
                "We reserve the right to restrict or deny your access to the database if we"
                    + " suspect that you have failed to comply with these terms.",
                "We reserve the right to modify this agreement at any time."))
            .setLinkValueSuffix("help/tos")
            .setLinkHrefUrlString("https://www.registry.tld/about/rdap/tos.html")
            .build());
    return rdapJsonFormatter;
  }
}
