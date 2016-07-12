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

package google.registry.flows.async;

import static google.registry.model.EppResourceUtils.isActive;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PipelineUtils.createJobPath;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import google.registry.dns.DnsQueue;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Enqueues DNS refreshes for applicable domains following a host rename.
 */
@Action(path = "/_dr/task/dnsRefreshForHostRename")
public class DnsRefreshForHostRenameAction implements Runnable {

  /** The HTTP parameter name used to specify the websafe key of the host to rename. */
  public static final String PARAM_HOST_KEY = "hostKey";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @NonFinalForTesting
  static DnsQueue dnsQueue = DnsQueue.create();

  @Inject @Parameter(PARAM_HOST_KEY) String hostKeyString;
  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject DnsRefreshForHostRenameAction() {}

  @Override
  public void run() {
    Key<HostResource> resourceKey = null;
    HostResource host;
    try {
      resourceKey = Key.create(hostKeyString);
      host = checkArgumentNotNull(ofy().load().key(resourceKey).now());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(resourceKey == null
          ? "Could not parse key string: " + hostKeyString
          : "Could not load resource for key: " + resourceKey);
    }
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Enqueue DNS refreshes for domains following a host rename")
        .setModuleName("backend")
        .runMapOnly(
            new DnsRefreshForHostRenameMapper(host),
            ImmutableList.of(EppResourceInputs.createEntityInput(DomainResource.class)))));
  }

  /** Map over domains and refresh the dns of those that referenced this host. */
  public static class DnsRefreshForHostRenameMapper extends Mapper<DomainResource, Void, Void> {

    private static final long serialVersionUID = -4707015136971008447L;

    private final DateTime hostUpdateTime;
    private final Key<HostResource> targetHostKey;

    DnsRefreshForHostRenameMapper(HostResource host) {
      this.targetHostKey = Key.create(host);
      this.hostUpdateTime = host.getUpdateAutoTimestamp().getTimestamp();
    }

    @Override
    public final void map(DomainResource domain) {
      if (isActive(domain, hostUpdateTime)
          && domain.getNameservers().contains(Ref.create(targetHostKey))) {
        try {
          dnsQueue.addDomainRefreshTask(domain.getFullyQualifiedDomainName());
          logger.infofmt("Enqueued refresh for domain %s", domain.getFullyQualifiedDomainName());
        } catch (Throwable t) {
          logger.severefmt(t, "Error while refreshing DNS for host rename %s", targetHostKey);
        }
      }
    }
  }
}
