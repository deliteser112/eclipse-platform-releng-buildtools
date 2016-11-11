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

package google.registry.tools.server;

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.model.EppResource;
import google.registry.request.Action;
import google.registry.request.Response;
import javax.inject.Inject;

/**
 * A mapreduce that re-saves all EppResources without otherwise modifying them.
 *
 * <p>This is useful for completing data migrations on EppResource fields that are accomplished
 * with @OnSave or @OnLoad annotations, and also guarantees that all EppResources will get fresh
 * commit logs (for backup purposes).
 */
@Action(path = "/_dr/task/resaveAllEppResources")
public class ResaveAllEppResourcesAction implements Runnable {

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject ResaveAllEppResourcesAction() {}

  @SuppressWarnings("unchecked")
  @Override
  public void run() {
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Re-save all EPP resources")
        .setModuleName("tools")
        .runMapOnly(
            new ResaveAllEppResourcesActionMapper(),
            ImmutableList.of(EppResourceInputs.createKeyInput(EppResource.class)))));
  }

  /** Mapper to re-save all EPP resources. */
  public static class ResaveAllEppResourcesActionMapper
      extends Mapper<Key<EppResource>, Void, Void> {

    private static final long serialVersionUID = -7721628665138087001L;
    public ResaveAllEppResourcesActionMapper() {}

    @Override
    public final void map(final Key<EppResource> resourceKey) {
      ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          ofy().save().entity(ofy().load().key(resourceKey).now()).now();
        }});
      getContext().incrementCounter(String.format("%s entities re-saved", resourceKey.getKind()));
    }
  }
}

