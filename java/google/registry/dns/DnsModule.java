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

import static google.registry.dns.DnsConstants.DNS_PUBLISH_PUSH_QUEUE_NAME;
import static google.registry.dns.DnsConstants.DNS_PULL_QUEUE_NAME;
import static google.registry.dns.PublishDnsUpdatesAction.DOMAINS_PARAM;
import static google.registry.dns.PublishDnsUpdatesAction.HOSTS_PARAM;
import static google.registry.dns.ReadDnsQueueAction.KEEP_TASKS_PARAM;
import static google.registry.request.RequestParameters.extractBooleanParameter;
import static google.registry.request.RequestParameters.extractEnumParameter;
import static google.registry.request.RequestParameters.extractRequiredParameter;
import static google.registry.request.RequestParameters.extractSetOfParameters;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import google.registry.dns.DnsConstants.TargetType;
import google.registry.dns.writer.DnsWriterZone;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import java.util.Set;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

/** Dagger module for the dns package. */
@Module
public abstract class DnsModule {

  @Binds
  @DnsWriterZone
  abstract String provideZoneName(@Parameter(RequestParameters.PARAM_TLD) String tld);

  @Provides
  @Named(DNS_PULL_QUEUE_NAME)
  static Queue provideDnsPullQueue() {
    return QueueFactory.getQueue(DNS_PULL_QUEUE_NAME);
  }

  @Provides
  @Named(DNS_PUBLISH_PUSH_QUEUE_NAME)
  static Queue provideDnsUpdatePushQueue() {
    return QueueFactory.getQueue(DNS_PUBLISH_PUSH_QUEUE_NAME);
  }

  @Provides
  @Parameter(DOMAINS_PARAM)
  static Set<String> provideDomains(HttpServletRequest req) {
    return extractSetOfParameters(req, DOMAINS_PARAM);
  }

  @Provides
  @Parameter(HOSTS_PARAM)
  static Set<String> provideHosts(HttpServletRequest req) {
    return extractSetOfParameters(req, HOSTS_PARAM);
  }

  @Provides
  @Parameter(KEEP_TASKS_PARAM)
  static boolean provideKeepTasks(HttpServletRequest req) {
    return extractBooleanParameter(req, KEEP_TASKS_PARAM);
  }

  @Provides
  @Parameter("domainOrHostName")
  static String provideName(HttpServletRequest req) {
    return extractRequiredParameter(req, "name");
  }

  @Provides
  @Parameter("type")
  static TargetType provideType(HttpServletRequest req) {
    return extractEnumParameter(req, TargetType.class, "type");
  }
}
