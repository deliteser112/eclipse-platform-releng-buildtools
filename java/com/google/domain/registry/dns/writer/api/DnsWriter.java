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

package com.google.domain.registry.dns.writer.api;

/**
 * Transaction object for sending an atomic batch of updates for a single zone to the DNS server.
 *
 * <p>Here's an example of how you would publish updates for a domain and host:
 * <pre>
 * &#064;Inject Provider&lt;DnsWriter&gt; dnsWriter;
 * try (DnsWriter writer = dnsWriter.get()) {
 *   writer.publishDomain(domainName);
 *   writer.publishHost(hostName);
 * }
 * </pre>
 */
public interface DnsWriter extends AutoCloseable {

  /**
   * Loads {@code domainName} from datastore and publishes its NS/DS records to the DNS server.
   *
   * @param domainName the fully qualified domain name
   */
  void publishDomain(String domainName);

  /**
   * Loads {@code hostName} from datastore and publishes its A/AAAA glue records to the DNS server.
   *
   * @param hostName the fully qualified host name
   */
  void publishHost(String hostName);

  /** Commits the updates to the DNS server atomically. */
  @Override
  void close();
}
