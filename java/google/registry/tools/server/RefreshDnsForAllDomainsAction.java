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

package google.registry.tools.server;

import static google.registry.mapreduce.inputs.EppResourceInputs.createEntityInput;
import static google.registry.model.EppResourceUtils.isActive;
import static google.registry.model.registry.Registries.assertTldsExist;
import static google.registry.request.RequestParameters.PARAM_TLDS;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import google.registry.dns.DnsQueue;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.domain.DomainBase;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.NonFinalForTesting;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * A mapreduce that enqueues DNS publish tasks on all active domains on the specified TLD(s).
 *
 * <p>This refreshes DNS both for all domain names and all in-bailiwick hostnames, as DNS writers
 * are responsible for enqueuing refresh tasks for subordinate hosts. So this action thus refreshes
 * DNS for everything applicable under all TLDs under management.
 *
 * <p>Because there are no auth settings in the {@link Action} annotation, this command can only be
 * run internally, or by pretending to be internal by setting the X-AppEngine-QueueName header,
 * which only admin users can do.
 */
@Action(
    service = Action.Service.TOOLS,
    path = "/_dr/task/refreshDnsForAllDomains",
    auth = Auth.AUTH_INTERNAL_OR_ADMIN)
public class RefreshDnsForAllDomainsAction implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject @Parameter(PARAM_TLDS) ImmutableSet<String> tlds;
  @Inject RefreshDnsForAllDomainsAction() {}

  @Override
  public void run() {
    assertTldsExist(tlds);
    mrRunner
        .setJobName("Refresh DNS for all domains")
        .setModuleName("tools")
        .setDefaultMapShards(10)
        .runMapOnly(
            new RefreshDnsForAllDomainsActionMapper(tlds),
            ImmutableList.of(createEntityInput(DomainBase.class)))
        .sendLinkToMapreduceConsole(response);
  }

  /** Mapper to refresh DNS for all active domain resources. */
  public static class RefreshDnsForAllDomainsActionMapper
      extends Mapper<DomainBase, Void, Void> {

    private static final long serialVersionUID = 1455544013508953083L;

    @NonFinalForTesting private static DnsQueue dnsQueue = DnsQueue.create();

    private final ImmutableSet<String> tlds;

    RefreshDnsForAllDomainsActionMapper(ImmutableSet<String> tlds) {
      this.tlds = tlds;
    }

    @Override
    public void map(final DomainBase domain) {
      String domainName = domain.getFullyQualifiedDomainName();
      if (tlds.contains(domain.getTld())) {
        if (isActive(domain, DateTime.now(DateTimeZone.UTC))) {
          try {
            dnsQueue.addDomainRefreshTask(domainName);
            getContext().incrementCounter("active domains refreshed");
          } catch (Throwable t) {
            logger.atSevere().withCause(t).log(
                "Error while refreshing DNS for domain %s", domainName);
            getContext().incrementCounter("active domains errored");
          }
        } else {
          getContext().incrementCounter("inactive domains skipped");
        }
      } else {
        getContext().incrementCounter("domains on non-targeted TLDs skipped");
      }
    }

    @VisibleForTesting
    public static DnsQueue setDnsQueueForTest(DnsQueue testQueue) {
      DnsQueue currentQueue = dnsQueue;
      dnsQueue = testQueue;
      return currentQueue;
    }
  }
}
