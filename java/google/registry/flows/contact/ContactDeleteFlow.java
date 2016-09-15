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

package google.registry.flows.contact;

import static google.registry.flows.ResourceFlowUtils.failfastForAsyncDelete;
import static google.registry.flows.ResourceFlowUtils.verifyNoDisallowedStatuses;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_ACTION_PENDING;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.config.ConfigModule.Config;
import google.registry.flows.EppException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.async.AsyncFlowEnqueuer;
import google.registry.flows.async.AsyncFlowUtils;
import google.registry.flows.async.DeleteContactResourceAction;
import google.registry.flows.async.DeleteEppResourceAction;
import google.registry.flows.exceptions.ResourceToMutateDoesNotExistException;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 * An EPP flow that deletes a contact resource.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.exceptions.ResourceStatusProhibitsOperationException}
 * @error {@link google.registry.flows.exceptions.ResourceToDeleteIsReferencedException}
 * @error {@link google.registry.flows.exceptions.ResourceToMutateDoesNotExistException}
 */
public class ContactDeleteFlow extends LoggedInFlow implements TransactionalFlow {

  private static final ImmutableSet<StatusValue> DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.LINKED,
      StatusValue.CLIENT_DELETE_PROHIBITED,
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_DELETE_PROHIBITED);

  private static final Function<DomainBase, ImmutableSet<?>> GET_REFERENCED_CONTACTS =
      new Function<DomainBase, ImmutableSet<?>>() {
        @Override
        public ImmutableSet<?> apply(DomainBase domain) {
          return domain.getReferencedContacts();
        }};

  @Inject AsyncFlowEnqueuer asyncFlowEnqueuer;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @Config("asyncDeleteFlowMapreduceDelay") Duration mapreduceDelay;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject ContactDeleteFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
  }

  @Override
  public final EppOutput run() throws EppException {
    failfastForAsyncDelete(targetId, now, ContactResource.class, GET_REFERENCED_CONTACTS);
    ContactResource existingResource = loadByUniqueId(ContactResource.class, targetId, now);
    if (existingResource == null) {
      throw new ResourceToMutateDoesNotExistException(ContactResource.class, targetId);
    }
    verifyNoDisallowedStatuses(existingResource, DISALLOWED_STATUSES);
    verifyOptionalAuthInfoForResource(authInfo, existingResource);
    if (!isSuperuser) {
      verifyResourceOwnership(clientId, existingResource);
    }
    AsyncFlowUtils.enqueueMapreduceAction(
        DeleteContactResourceAction.class,
        ImmutableMap.of(
            DeleteEppResourceAction.PARAM_RESOURCE_KEY,
            Key.create(existingResource).getString(),
            DeleteEppResourceAction.PARAM_REQUESTING_CLIENT_ID,
            clientId,
            DeleteEppResourceAction.PARAM_IS_SUPERUSER,
            Boolean.toString(isSuperuser)),
        mapreduceDelay);
    // TODO(b/26140521): Switch over to batch async operations as follows:
    // asyncFlowEnqueuer.enqueueAsyncDelete(existingResource, getClientId(), isSuperuser);
    ContactResource newResource =
        existingResource.asBuilder().addStatusValue(StatusValue.PENDING_DELETE).build();
    historyBuilder
        .setType(HistoryEntry.Type.CONTACT_PENDING_DELETE)
        .setModificationTime(now)
        .setParent(Key.create(existingResource));
    ofy().save().<Object>entities(newResource, historyBuilder.build());
    return createOutput(SUCCESS_WITH_ACTION_PENDING);
  }
}
