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

package google.registry.dns.writer.clouddns;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.dns.Dns;
import com.google.api.services.dns.DnsScopes;
import com.google.common.base.Function;
import com.google.common.util.concurrent.RateLimiter;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import google.registry.config.ConfigModule.Config;
import google.registry.dns.writer.DnsWriter;
import java.util.Set;
import javax.inject.Named;

/** Dagger module for Google Cloud DNS service connection objects. */
@Module
public final class CloudDnsWriterModule {

  @Provides
  static Dns provideDns(
      HttpTransport transport,
      JsonFactory jsonFactory,
      Function<Set<String>, ? extends HttpRequestInitializer> credential,
      @Config("projectId") String projectId) {
    return new Dns.Builder(transport, jsonFactory, credential.apply(DnsScopes.all()))
        .setApplicationName(projectId)
        .build();
  }

  @Provides
  @IntoMap
  @StringKey(CloudDnsWriter.NAME)
  static DnsWriter provideWriter(CloudDnsWriter writer) {
    return writer;
  }

  @Provides
  @IntoSet
  @Named("dnsWriterNames")
  static String provideWriterName() {
    return CloudDnsWriter.NAME;
  }

  @Provides
  @Named("cloudDns")
  static RateLimiter provideRateLimiter() {
    // This is the default max QPS for Cloud DNS. It can be increased by contacting the team
    // via the Quotas page on the Cloud Console.
    int cloudDnsMaxQps = 20;
    return RateLimiter.create(cloudDnsMaxQps);
  }
}
