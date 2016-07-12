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

package google.registry.flows;

import static com.google.common.base.Preconditions.checkState;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.googlecode.objectify.Key;
import google.registry.flows.EppException.AuthorizationErrorException;
import google.registry.flows.FlowModule.InputXml;
import google.registry.model.EppResource;
import google.registry.model.domain.Period;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.reporting.HistoryEntry;
import google.registry.util.TypeUtils.TypeInstantiator;
import javax.inject.Inject;

/**
 * An EPP flow that creates or mutates a single stored resource.
 *
 * @param <R> the resource type being changed
 * @param <C> the command type, marshalled directly from the epp xml
 *
 * @error {@link OnlyToolCanPassMetadataException}
 */
public abstract class ResourceCreateOrMutateFlow
    <R extends EppResource, C extends SingleResourceCommand> extends SingleResourceFlow<R, C>
    implements TransactionalFlow {

  @Inject EppRequestSource eppRequestSource;
  @Inject @InputXml byte[] inputXmlBytes;

  String repoId;
  protected R newResource;
  protected HistoryEntry historyEntry;
  protected MetadataExtension metadataExtension;

  @Override
  protected final void initSingleResourceFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
    metadataExtension = eppInput.getSingleExtension(MetadataExtension.class);
    initRepoId();
    initHistoryEntry();
    initResourceCreateOrMutateFlow();
  }

  /** Subclasses can optionally override this for further initialization. */
  @SuppressWarnings("unused")
  protected void initResourceCreateOrMutateFlow() throws EppException {}

  /**
   * Initializes the repoId on the flow. For mutate flows, the repoId is the same as that of the
   * existing resource. For create flows, a new repoId is allocated for the appropriate class.
   */
  protected abstract void initRepoId();

  /**
   * Create the history entry associated with this resource create or mutate flow.
   */
  private void initHistoryEntry() {
    // Don't try to create a historyEntry for mutate flows that are failing because the
    // existingResource doesn't actually exist.
    historyEntry = (repoId == null) ? null : new HistoryEntry.Builder()
        .setType(getHistoryEntryType())
        .setPeriod(getCommandPeriod())
        .setClientId(getClientId())
        .setTrid(trid)
        .setModificationTime(now)
        .setXmlBytes(storeXmlInHistoryEntry() ? inputXmlBytes : null)
        .setBySuperuser(isSuperuser)
        .setReason(getHistoryEntryReason())
        .setRequestedByRegistrar(getHistoryEntryRequestedByRegistrar())
        .setParent(getResourceKey())
        .build();
  }

  /**
   * Returns a Key pointing to this resource, even if this resource hasn't been initialized or
   * persisted yet.
   */
  protected Key<EppResource> getResourceKey() {
    checkState(repoId != null,
        "RepoId hasn't been initialized yet; getResourceKey() called too early");
    Class<R> resourceClazz = new TypeInstantiator<R>(getClass()){}.getExactType();
    return Key.<EppResource>create(null, resourceClazz, repoId);
  }

  @Override
  protected final EppOutput runResourceFlow() throws EppException {
    newResource = createOrMutateResource();
    verifyNewStateIsAllowed();
    validateMetadataExtension();
    modifyRelatedResources();
    enqueueTasks();
    ofy().save().<Object>entities(newResource, historyEntry);
    return getOutput();
  }

  /** Execute the inner core of the command and returned the created or mutated resource. */
  protected abstract R createOrMutateResource() throws EppException;

  /** Check the new state before writing it. */
  @SuppressWarnings("unused")
  protected void verifyNewStateIsAllowed() throws EppException {}

  /** Kick off any tasks that need to happen asynchronously. */
  @SuppressWarnings("unused")
  protected void enqueueTasks() throws EppException {}

  /** Modify any other resources that need to be informed of this change. */
  @SuppressWarnings("unused")
  protected void modifyRelatedResources() throws EppException {}

  /** Ensure that, if a metadata command exists, it is being passed from a tool-created session. */
  void validateMetadataExtension() throws EppException {
    if (!(metadataExtension == null || eppRequestSource.equals(EppRequestSource.TOOL))) {
      throw new OnlyToolCanPassMetadataException();
    }
  }

  /** Subclasses must override this to specify the type set on the history entry. */
  protected abstract HistoryEntry.Type getHistoryEntryType();

  /** Subclasses may override this if they do not wish to store the XML of a command. */
  protected boolean storeXmlInHistoryEntry() { return true; }

  /** Retrieve the reason for the history entry. */
  protected String getHistoryEntryReason() {
    return metadataExtension != null
        ? metadataExtension.getReason()
        : null;
  }

  /** Retrieve the requested by registrar flag for the history entry. */
  protected Boolean getHistoryEntryRequestedByRegistrar() {
    return metadataExtension != null
        ? metadataExtension.getRequestedByRegistrar()
        : null;
  }

  /**
   * Subclasses that have a specified period for their command should override this to so that the
   * history entry contains the correct data.
   */
  protected Period getCommandPeriod() { return null; }

  /** Get the {@link EppOutput} to return. */
  protected abstract EppOutput getOutput() throws EppException;

  /** Only a tool can pass a metadata extension. */
  public static class OnlyToolCanPassMetadataException extends AuthorizationErrorException {
    public OnlyToolCanPassMetadataException() {
      super("Metadata extensions can only be passed by tools.");
    }
  }

}
