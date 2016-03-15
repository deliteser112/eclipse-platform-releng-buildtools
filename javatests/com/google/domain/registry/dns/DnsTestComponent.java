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

package com.google.domain.registry.dns;

import com.google.domain.registry.config.ConfigModule;
import com.google.domain.registry.dns.writer.api.VoidDnsWriterModule;
import com.google.domain.registry.module.backend.BackendModule;
import com.google.domain.registry.request.RequestModule;
import com.google.domain.registry.util.SystemClock.SystemClockModule;

import dagger.Component;

@Component(modules = {
    SystemClockModule.class,
    ConfigModule.class,
    BackendModule.class,
    DnsModule.class,
    RequestModule.class,
    VoidDnsWriterModule.class,
})
interface DnsTestComponent {
  DnsQueue dnsQueue();
  RefreshDnsAction refreshDns();
  WriteDnsAction writeDnsAction();
}
