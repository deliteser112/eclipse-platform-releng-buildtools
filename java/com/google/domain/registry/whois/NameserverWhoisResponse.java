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

package com.google.domain.registry.whois;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.registrar.Registrar;

import org.joda.time.DateTime;

import java.net.InetAddress;

/** Container for WHOIS responses to a nameserver lookup queries. */
final class NameserverWhoisResponse extends WhoisResponseImpl {

  /** Nameserver(s) which were the target of this WHOIS command. */
  private final ImmutableList<HostResource> hosts;

  /** Creates new WHOIS nameserver response on the given host. */
  NameserverWhoisResponse(HostResource host, DateTime timestamp) {
    this(ImmutableList.of(checkNotNull(host, "host")), timestamp);
  }

  /** Creates new WHOIS nameserver response on the given list of hosts. */
  NameserverWhoisResponse(ImmutableList<HostResource> hosts, DateTime timestamp) {
    super(timestamp);
    this.hosts = checkNotNull(hosts, "hosts");
  }

  @Override
  public String getPlainTextOutput(boolean preferUnicode) {
    BasicEmitter emitter = new BasicEmitter();
    for (HostResource host : hosts) {
      Registrar registrar = getRegistrar(host.getCurrentSponsorClientId());
      emitter
          .emitField("Server Name", maybeFormatHostname(
              host.getFullyQualifiedHostName(), preferUnicode))
          .emitSet("IP Address", host.getInetAddresses(),
              new Function<InetAddress, String>() {
                @Override
                public String apply(InetAddress addr) {
                  return InetAddresses.toAddrString(addr);
                }})
          .emitField("Registrar", registrar.getRegistrarName())
          .emitField("Registrar WHOIS Server", registrar.getWhoisServer())
          .emitField("Registrar URL", registrar.getReferralUrl())
          .emitNewline();
    }
    return emitter.emitFooter(getTimestamp()).toString();
  }
}
