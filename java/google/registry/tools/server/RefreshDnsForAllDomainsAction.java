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
import static google.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import google.registry.dns.DnsQueue;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.EppResourceUtils;
import google.registry.model.domain.DomainResource;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.request.auth.AuthLevel;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * A mapreduce that enqueues DNS publish tasks on all active domains.
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
  path = "/_dr/task/refreshDnsForAllDomains",
  auth =
      @Auth(
        methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API},
        minimumLevel = AuthLevel.APP,
        userPolicy = Auth.UserPolicy.ADMIN
      )
)
public class RefreshDnsForAllDomainsAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject RefreshDnsForAllDomainsAction() {}

  @Override
  public void run() {
    response.sendJavaScriptRedirect(
        createJobPath(
            mrRunner
                .setJobName("Refresh all domains")
                .setModuleName("tools")
                .setDefaultMapShards(10)
                .runMapOnly(
                    new RefreshDnsForAllDomainsActionMapper(),
                    ImmutableList.of(createEntityInput(DomainResource.class)))));
  }

  /** Mapper to refresh DNS for all active domain resources. */
  public static class RefreshDnsForAllDomainsActionMapper
      extends Mapper<DomainResource, Void, Void> {

    private static final DnsQueue dnsQueue = DnsQueue.create();
    private static final long serialVersionUID = 1356876487351666133L;

    @Override
    public void map(final DomainResource domain) {
      String domainName = domain.getFullyQualifiedDomainName();
      if (EppResourceUtils.isActive(domain, DateTime.now(DateTimeZone.UTC))) {
        try {
          dnsQueue.addDomainRefreshTask(domainName);
          getContext().incrementCounter("active domains refreshed");
        } catch (Throwable t) {
          logger.severefmt(t, "Error while refreshing DNS for domain %s", domainName);
          getContext().incrementCounter("active domains errored");
        }
      } else {
        getContext().incrementCounter("inactive domains skipped");
      }
    }
  }
}
