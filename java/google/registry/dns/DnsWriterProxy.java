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

package google.registry.dns;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import google.registry.dns.writer.DnsWriter;
import google.registry.model.registry.Registry;
import java.util.Map;
import javax.inject.Inject;

/** Proxy for retrieving {@link DnsWriter} implementations. */
public final class DnsWriterProxy {

  private final ImmutableMap<String, DnsWriter> dnsWriters;

  @Inject
  DnsWriterProxy(Map<String, DnsWriter> dnsWriters) {
    this.dnsWriters = ImmutableMap.copyOf(dnsWriters);
  }

  /** Return the {@link DnsWriter} for the given tld. */
  public DnsWriter getForTld(String tld) {
    String clazz = Registry.get(tld).getDnsWriter();
    DnsWriter dnsWriter = dnsWriters.get(clazz);
    checkState(dnsWriter != null, "Could not load DnsWriter %s for TLD %s", clazz, tld);
    return dnsWriter;
  }
}
