// Copyright 2019 The Nomulus Authors. All Rights Reserved.
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
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.POST;
import static google.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.googlecode.objectify.Key;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.domain.DomainApplication;
import google.registry.model.index.DomainApplicationIndex;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.reporting.HistoryEntry;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import javax.inject.Inject;

/**
 * Deletes all {@link DomainApplication} entities in Datastore.
 *
 * <p>This also deletes the corresponding {@link DomainApplicationIndex}, {@link EppResourceIndex},
 * and descendent {@link HistoryEntry}s.
 */
@Action(path = "/_dr/task/killAllDomainApplications", method = POST, auth = Auth.AUTH_INTERNAL_ONLY)
public class KillAllDomainApplicationsAction implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;

  @Inject
  KillAllDomainApplicationsAction() {}

  @Override
  public void run() {
    response.sendJavaScriptRedirect(
        createJobPath(
            mrRunner
                .setJobName("Delete all domain applications and associated entities")
                .setModuleName("tools")
                .runMapOnly(
                    new KillAllDomainApplicationsMapper(),
                    ImmutableList.of(createEntityInput(DomainApplication.class)))));
  }

  static class KillAllDomainApplicationsMapper extends Mapper<DomainApplication, Void, Void> {

    private static final long serialVersionUID = 2862967335000340688L;

    @Override
    public void map(final DomainApplication application) {
      ofy()
          .transact(
              () -> {
                if (ofy().load().entity(application).now() == null) {
                  getContext().incrementCounter("applications already deleted");
                  return;
                }
                Key<DomainApplication> applicationKey = Key.create(application);
                DomainApplicationIndex dai =
                    ofy().load().key(DomainApplicationIndex.createKey(application)).now();
                EppResourceIndex eri =
                    ofy().load().entity(EppResourceIndex.create(applicationKey)).now();
                if (dai == null || eri == null) {
                  logger.atSevere().log(
                      "Missing index(es) for application %s; skipping.", applicationKey);
                  getContext().incrementCounter("missing indexes");
                  return;
                }
                // Delete the application, its descendents, and the indexes.
                ofy().delete().keys(ofy().load().ancestor(application).keys());
                ofy().delete().entities(dai, eri);
                logger.atInfo().log("Deleted domain application %s.", applicationKey);
                getContext().incrementCounter("applications deleted");
              });
    }
  }
}
