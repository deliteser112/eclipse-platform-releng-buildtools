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

package google.registry.monitoring.whitebox;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.googlecode.objectify.Key.getKind;
import static google.registry.model.EppResourceUtils.isActive;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.POST;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static google.registry.util.DateTimeUtils.earliestOf;
import static google.registry.util.DateTimeUtils.isAtOrAfter;
import static google.registry.util.DateTimeUtils.isBeforeOrAt;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;
import static google.registry.util.PipelineUtils.createJobPath;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.appengine.tools.mapreduce.inputs.DatastoreKeyInput;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.googlecode.objectify.Key;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.host.HostResource;
import google.registry.model.index.DomainApplicationIndex;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.index.ForeignKeyIndex.ForeignKeyContactIndex;
import google.registry.model.index.ForeignKeyIndex.ForeignKeyDomainIndex;
import google.registry.model.index.ForeignKeyIndex.ForeignKeyHostIndex;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * A mapreduce to verify integrity of entities in Datastore.
 *
 * <p>Specifically this validates all of the following system invariants that are expected to hold
 * true for all {@link EppResource} entities and their related indexes:
 * <ul>
 *   <li>All {@link Key} fields (including nested ones) point to entities that exist.
 *   <li>There is exactly one {@link EppResourceIndex} pointing to each {@link EppResource}.
 *   <li>All contacts, hosts, and domains, when grouped by foreign key, have at most one active
 *       resource, and exactly one {@link ForeignKeyIndex} of the appropriate type, which points to
 *       the active resource if one exists, or to the most recently deleted resource if not.  The
 *       foreignKey and deletionTime fields on the index must also match the respective resource(s).
 *   <li>All domain applications, when grouped by foreign key, have exactly one
 *       {@link DomainApplicationIndex} that links to all of them, and has a matching
 *       fullyQualifiedDomainName.
 * </ul>
 */
@Action(path = "/_dr/task/verifyEntityIntegrity", method = POST)
public class VerifyEntityIntegrityAction implements Runnable {

  private static final FormattingLogger logger = getLoggerForCallerClass();
  private static final int NUM_SHARDS = 200;
  @NonFinalForTesting
  @VisibleForTesting
  static WhiteboxComponent component = DaggerWhiteboxComponent.create();
  private static final ImmutableSet<Class<?>> RESOURCE_CLASSES =
      ImmutableSet.<Class<?>>of(
          ForeignKeyDomainIndex.class,
          DomainApplicationIndex.class,
          ForeignKeyHostIndex.class,
          ForeignKeyContactIndex.class,
          DomainBase.class,
          HostResource.class,
          ContactResource.class);

  static final String KIND_CONTACT_RESOURCE = getKind(ContactResource.class);
  static final String KIND_CONTACT_INDEX = getKind(ForeignKeyContactIndex.class);
  static final String KIND_DOMAIN_APPLICATION_INDEX = getKind(DomainApplicationIndex.class);
  static final String KIND_DOMAIN_BASE_RESOURCE = getKind(DomainBase.class);
  static final String KIND_DOMAIN_INDEX = getKind(ForeignKeyDomainIndex.class);
  static final String KIND_EPPRESOURCE_INDEX = getKind(EppResourceIndex.class);
  static final String KIND_HOST_RESOURCE = getKind(HostResource.class);
  static final String KIND_HOST_INDEX = getKind(ForeignKeyHostIndex.class);

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject VerifyEntityIntegrityAction() {}

  @Override
  public void run() {
    DateTime scanTime = DateTime.now(UTC);
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Verify entity integrity")
        .setModuleName("backend")
        .setDefaultReduceShards(NUM_SHARDS)
        .runMapreduce(
            new VerifyEntityIntegrityMapper(scanTime),
            new VerifyEntityIntegrityReducer(scanTime),
            getInputs())));
  }

  private static ImmutableSet<Input<? extends Object>> getInputs() {
    ImmutableSet.Builder<Input<? extends Object>> builder =
        new ImmutableSet.Builder<Input<? extends Object>>()
            .add(EppResourceInputs.createIndexInput());
    for (Class<?> clazz : RESOURCE_CLASSES) {
      builder.add(new DatastoreKeyInput(getKind(clazz), NUM_SHARDS));
    }
    return builder.build();
  }

  /**
   * The mapreduce key that the mapper outputs.  Each {@link EppResource} has two different
   * mapreduce keys that are output for it: one for its specific type (domain, application, host, or
   * contact), which is used to check {@link ForeignKeyIndex} constraints, and one that is common
   * for all EppResources, to check {@link EppResourceIndex} constraints.
   */
  private static enum EntityKind {
    DOMAIN,
    APPLICATION,
    CONTACT,
    HOST,
    /**
     * Used to verify 1-to-1 constraints between all types of EPP resources and their indexes.
     */
    EPP_RESOURCE
  }

  private static class MapperKey implements Serializable {

    private static final long serialVersionUID = 3222302549441420932L;

    /**
     * The relevant id for this mapper key, which is either the foreign key of the EppResource (for
     * verifying foreign key indexes) or its repoId (for verifying EppResourceIndexes).
     */
    public String id;
    public EntityKind kind;

    public static MapperKey create(EntityKind kind, String id) {
      MapperKey instance = new MapperKey();
      instance.kind = kind;
      instance.id = id;
      return instance;
    }
  }

  /**
   * Mapper that checks validity of references on all resources and outputs key/value pairs used to
   * check integrity of foreign key entities.
   */
  public static class VerifyEntityIntegrityMapper
      extends Mapper<Object, MapperKey, Key<? extends ImmutableObject>> {

    private static final long serialVersionUID = -5413882340475018051L;
    private final DateTime scanTime;

    private transient VerifyEntityIntegrityStreamer integrityStreamer;

    // The integrityStreamer field must be marked as transient so that instances of the Mapper class
    // can be serialized by the MapReduce framework.  Thus, every time is used, lazily construct it
    // if it doesn't exist yet.
    private VerifyEntityIntegrityStreamer integrity() {
      if (integrityStreamer == null) {
        integrityStreamer = component.verifyEntityIntegrityStreamerFactory().create(scanTime);
      }
      return integrityStreamer;
    }

    public VerifyEntityIntegrityMapper(DateTime scanTime) {
      this.scanTime = scanTime;
    }

    @Override
    public final void map(Object keyOrEntity) {
      try {
        // We use different inputs, some that return keys and some that return entities.  Load any
        // keys that we get so we're dealing only with entities.
        if (keyOrEntity instanceof com.google.appengine.api.datastore.Key) {
          Key<?> key = Key.create((com.google.appengine.api.datastore.Key) keyOrEntity);
          keyOrEntity = ofy().load().key(key).now();
        }
        mapEntity(keyOrEntity);
      } catch (Throwable e) {
        // Log and swallow so that the mapreduce doesn't abort on first error.
        logger.severefmt(e, "Exception while checking integrity of entity: %s", keyOrEntity);
      }
    }

    private void mapEntity(Object entity) {
      if (entity instanceof EppResource) {
        mapEppResource((EppResource) entity);
      } else if (entity instanceof ForeignKeyIndex<?>) {
        mapForeignKeyIndex((ForeignKeyIndex<?>) entity);
      } else if (entity instanceof DomainApplicationIndex) {
        mapDomainApplicationIndex((DomainApplicationIndex) entity);
      } else if (entity instanceof EppResourceIndex) {
        mapEppResourceIndex((EppResourceIndex) entity);
      } else {
        throw new IllegalStateException(
            String.format("Unknown entity in integrity mapper: %s", entity));
      }
    }

    private void mapEppResource(EppResource resource) {
      emit(MapperKey.create(EntityKind.EPP_RESOURCE, resource.getRepoId()), Key.create(resource));
      if (resource instanceof DomainBase) {
        DomainBase domainBase = (DomainBase) resource;
        Key<?> key = Key.create(domainBase);
        verifyExistence(key, domainBase.getReferencedContacts());
        verifyExistence(key, domainBase.getNameservers());
        verifyExistence(key, domainBase.getTransferData().getServerApproveAutorenewEvent());
        verifyExistence(key, domainBase.getTransferData().getServerApproveAutorenewPollMessage());
        verifyExistence(key, domainBase.getTransferData().getServerApproveBillingEvent());
        verifyExistence(key, FluentIterable
            .from(domainBase.getTransferData().getServerApproveEntities())
            .transform(
              new Function<Key<? extends TransferServerApproveEntity>,
                  Key<TransferServerApproveEntity>>() {
                @SuppressWarnings("unchecked")
                @Override
                public Key<TransferServerApproveEntity> apply(
                    Key<? extends TransferServerApproveEntity> key) {
                  return (Key<TransferServerApproveEntity>) key;
                }})
            .toSet());
        if (domainBase instanceof DomainApplication) {
          getContext().incrementCounter("domain applications");
          DomainApplication application = (DomainApplication) domainBase;
          emit(
              MapperKey.create(EntityKind.APPLICATION, application.getFullyQualifiedDomainName()),
              Key.create(application));
        } else if (domainBase instanceof DomainResource) {
          getContext().incrementCounter("domain resources");
          DomainResource domain = (DomainResource) domainBase;
          verifyExistence(key, domain.getApplication());
          verifyExistence(key, domain.getAutorenewBillingEvent());
          for (GracePeriod gracePeriod : domain.getGracePeriods()) {
            verifyExistence(key, gracePeriod.getOneTimeBillingEvent());
            verifyExistence(key, gracePeriod.getRecurringBillingEvent());
          }
          emit(
              MapperKey.create(EntityKind.DOMAIN, domain.getFullyQualifiedDomainName()),
              Key.create(domain));
        }
      } else if (resource instanceof ContactResource) {
        getContext().incrementCounter("contact resources");
        ContactResource contact = (ContactResource) resource;
        emit(
            MapperKey.create(EntityKind.CONTACT, contact.getContactId()),
            Key.create(contact));
      } else if (resource instanceof HostResource) {
        getContext().incrementCounter("host resources");
        HostResource host = (HostResource) resource;
        verifyExistence(Key.create(host), host.getSuperordinateDomain());
        emit(
            MapperKey.create(EntityKind.HOST, host.getFullyQualifiedHostName()),
            Key.create(host));
      } else {
        throw new IllegalStateException(
            String.format("EppResource with unknown type in integrity mapper: %s", resource));
      }
    }

    private void mapForeignKeyIndex(ForeignKeyIndex<?> fki) {
      Key<ForeignKeyIndex<?>> fkiKey = Key.<ForeignKeyIndex<?>>create(fki);
      @SuppressWarnings("cast")
      EppResource resource = verifyExistence(fkiKey, fki.getResourceKey());
      if (resource != null) {
        // TODO(user): Traverse the chain of pointers to old FKIs instead once they are written.
        if (isAtOrAfter(fki.getDeletionTime(), resource.getDeletionTime())) {
          integrity().check(
              fki.getForeignKey().equals(resource.getForeignKey()),
              fkiKey,
              Key.create(resource),
              "Foreign key index points to EppResource with different foreign key");
        }
      }
      if (fki instanceof ForeignKeyDomainIndex) {
        getContext().incrementCounter("domain foreign key indexes");
        emit(MapperKey.create(EntityKind.DOMAIN, fki.getForeignKey()), fkiKey);
      } else if (fki instanceof ForeignKeyContactIndex) {
        getContext().incrementCounter("contact foreign key indexes");
        emit(MapperKey.create(EntityKind.CONTACT, fki.getForeignKey()), fkiKey);
      } else if (fki instanceof ForeignKeyHostIndex) {
        getContext().incrementCounter("host foreign key indexes");
        emit(MapperKey.create(EntityKind.HOST, fki.getForeignKey()), fkiKey);
      } else {
        throw new IllegalStateException(
            String.format("Foreign key index is of unknown type: %s", fki));
      }
    }

    private void mapDomainApplicationIndex(DomainApplicationIndex dai) {
      getContext().incrementCounter("domain application indexes");
      Key<DomainApplicationIndex> daiKey = Key.create(dai);
      for (Key<DomainApplication> key : dai.getKeys()) {
        DomainApplication application = verifyExistence(daiKey, key);
        if (application != null) {
          integrity().check(
              dai.getFullyQualifiedDomainName().equals(application.getFullyQualifiedDomainName()),
              daiKey,
              Key.create(application),
              "Domain application index points to application with different domain name");
        }
        emit(
            MapperKey.create(EntityKind.APPLICATION, dai.getFullyQualifiedDomainName()),
            daiKey);
      }
    }

    private void mapEppResourceIndex(EppResourceIndex eri) {
      Key<EppResourceIndex> eriKey = Key.create(eri);
      String eriRepoId = Key.create(eri.getId()).getName();
      integrity().check(
          eriRepoId.equals(eri.getKey().getName()),
          eriKey,
          eri.getKey(),
          "EPP resource index id does not match repoId of reference");
      verifyExistence(eriKey, eri.getKey());
      emit(MapperKey.create(EntityKind.EPP_RESOURCE, eriRepoId), eriKey);
      getContext().incrementCounter("EPP resource indexes to " + eri.getKind());
    }

    private <E> void verifyExistence(Key<?> source, Set<Key<E>> targets) {
      Set<Key<E>> missingEntityKeys =
          Sets.difference(targets, ofy().load().<E>keys(targets).keySet());
      integrity().checkOneToMany(
          missingEntityKeys.isEmpty(),
          source,
          targets,
          "Target entity does not exist");
    }

    @Nullable
    private <E> E verifyExistence(Key<?> source, @Nullable Key<E> target) {
      if (target == null) {
        return null;
      }
      E entity = ofy().load().key(target).now();
      integrity().check(entity != null, source, target, "Target entity does not exist");
      return entity;
    }
  }

  /** Reducer that checks integrity of foreign key entities. */
  public static class VerifyEntityIntegrityReducer
      extends Reducer<MapperKey, Key<? extends ImmutableObject>, Void> {

    private static final long serialVersionUID = -151271247606894783L;

    private final DateTime scanTime;

    private transient VerifyEntityIntegrityStreamer integrityStreamer;

    // The integrityStreamer field must be marked as transient so that instances of the Reducer
    // class can be serialized by the MapReduce framework.  Thus, every time is used, lazily
    // construct it if it doesn't exist yet.
    private VerifyEntityIntegrityStreamer integrity() {
      if (integrityStreamer == null) {
        integrityStreamer = component.verifyEntityIntegrityStreamerFactory().create(scanTime);
      }
      return integrityStreamer;
    }

    public VerifyEntityIntegrityReducer(DateTime scanTime) {
      this.scanTime = scanTime;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void reduce(MapperKey mapperKey, ReducerInput<Key<? extends ImmutableObject>> keys) {
      try {
        reduceKeys(mapperKey, keys);
      } catch (Throwable e) {
        // Log and swallow so that the mapreduce doesn't abort on first error.
        logger.severefmt(
            e, "Exception while checking foreign key integrity constraints for: %s", mapperKey);
      }
    }

    private void reduceKeys(
        MapperKey mapperKey, ReducerInput<Key<? extends ImmutableObject>> keys) {
      getContext().incrementCounter("reduced resources " + mapperKey.kind);
      switch (mapperKey.kind) {
        case EPP_RESOURCE:
          checkEppResourceIndexes(keys, mapperKey.id);
          break;
        case APPLICATION:
          checkIndexes(
              keys,
              mapperKey.id,
              KIND_DOMAIN_BASE_RESOURCE,
              KIND_DOMAIN_APPLICATION_INDEX,
              false);
          break;
        case CONTACT:
          checkIndexes(keys, mapperKey.id, KIND_CONTACT_RESOURCE, KIND_CONTACT_INDEX, true);
          break;
        case DOMAIN:
          checkIndexes(
              keys, mapperKey.id, KIND_DOMAIN_BASE_RESOURCE, KIND_DOMAIN_INDEX, true);
          break;
        case HOST:
          checkIndexes(keys, mapperKey.id, KIND_HOST_RESOURCE, KIND_HOST_INDEX, true);
          break;
        default:
          throw new IllegalStateException(
              String.format("Unknown type of foreign key %s", mapperKey.kind));
      }
    }

    @SuppressWarnings("unchecked")
    private void checkEppResourceIndexes(
        Iterator<Key<? extends ImmutableObject>> keys, String repoId) {
      List<Key<EppResource>> resources = new ArrayList<>();
      List<Key<EppResourceIndex>> eppResourceIndexes = new ArrayList<>();
      while (keys.hasNext()) {
        Key<?> key = keys.next();
        String kind = key.getKind();
        if (kind.equals(KIND_EPPRESOURCE_INDEX)) {
          eppResourceIndexes.add((Key<EppResourceIndex>) key);
        } else if (kind.equals(KIND_DOMAIN_BASE_RESOURCE)
            || kind.equals(KIND_CONTACT_RESOURCE)
            || kind.equals(KIND_HOST_RESOURCE)) {
          resources.add((Key<EppResource>) key);
        } else {
          throw new IllegalStateException(
              String.format(
                  "While verifying EppResourceIndexes for repoId %s, found key of unknown type: %s",
                  repoId,
                  key));
        }
      }
      // This is a checkState and not an integrity check because the Datastore schema ensures that
      // there can't be multiple EppResources with the same repoId.
      checkState(
          resources.size() == 1,
          String.format("Found more than one EppResource for repoId %s: %s", repoId, resources));
      if (integrity().check(
          !eppResourceIndexes.isEmpty(),
          null,
          getOnlyElement(resources),
          "Missing EPP resource index for EPP resource")) {
        integrity().checkManyToOne(
            eppResourceIndexes.size() == 1,
            eppResourceIndexes,
            getOnlyElement(resources),
            "Duplicate EPP resource indexes pointing to same resource");
      }
    }

    @SuppressWarnings("unchecked")
    private <R extends EppResource, I> void checkIndexes(
        Iterator<Key<? extends ImmutableObject>> keys,
        String foreignKey,
        String resourceKind,
        String foreignKeyIndexKind,
        boolean thereCanBeOnlyOne) {
      List<Key<R>> resources = new ArrayList<>();
      List<Key<I>> foreignKeyIndexes = new ArrayList<>();
      while (keys.hasNext()) {
        Key<?> key = keys.next();
        if (key.getKind().equals(resourceKind)) {
          resources.add((Key<R>) key);
        } else if (key.getKind().equals(foreignKeyIndexKind)) {
          foreignKeyIndexes.add((Key<I>) key);
        } else {
          throw new IllegalStateException(
              String.format(
                  "While processing links to foreign key %s of type %s, found unknown key: %s",
                  foreignKey,
                  resourceKind,
                  key));
        }
      }
      // This is a checkState and not an integrity check because it should truly be impossible to
      // have multiple foreign key indexes for the same foreign key because of the Datastore schema.
      checkState(
          foreignKeyIndexes.size() <= 1,
          String.format(
              "Found more than one foreign key index for %s: %s", foreignKey, foreignKeyIndexes));
      integrity().check(
          !foreignKeyIndexes.isEmpty(),
          foreignKey,
          resourceKind,
          "Missing foreign key index for EppResource");
      // Skip the case where no resources were found because entity exceptions are already thrown in
      // the mapper in invalid situations where FKIs point to non-existent entities.
      if (thereCanBeOnlyOne && !resources.isEmpty()) {
        verifyOnlyOneActiveResource(resources, getOnlyElement(foreignKeyIndexes));
      }
    }

    private <R extends EppResource, I> void verifyOnlyOneActiveResource(
        List<Key<R>> resources, Key<I> fkiKey) {
      DateTime oldestActive = END_OF_TIME;
      DateTime mostRecentInactive = START_OF_TIME;
      Key<R> mostRecentInactiveKey = null;
      List<Key<R>> activeResources = new ArrayList<>();
      Map<Key<R>, R> allResources = ofy().load().keys(resources);
      ForeignKeyIndex<?> fki = (ForeignKeyIndex<?>) ofy().load().key(fkiKey).now();
      for (Map.Entry<Key<R>, R> entry : allResources.entrySet()) {
        R resource = entry.getValue();
        if (isActive(resource, scanTime)) {
          activeResources.add(entry.getKey());
          oldestActive = earliestOf(oldestActive, resource.getCreationTime());
        } else {
          if (resource.getDeletionTime().isAfter(mostRecentInactive)) {
            mostRecentInactive = resource.getDeletionTime();
            mostRecentInactiveKey = entry.getKey();
          }
        }
      }
      if (activeResources.isEmpty()) {
        integrity().check(
            fki.getDeletionTime().isEqual(mostRecentInactive),
            fkiKey,
            mostRecentInactiveKey,
            "Foreign key index deletion time not equal to that of most recently deleted resource");
      } else {
        integrity().checkOneToMany(
            activeResources.size() == 1,
            fkiKey,
            activeResources,
            "Multiple active EppResources with same foreign key");
        integrity().check(
            fki.getDeletionTime().isEqual(END_OF_TIME),
            fkiKey,
            null,
            "Foreign key index has deletion time but active resource exists");
        integrity().check(
            isBeforeOrAt(mostRecentInactive, oldestActive),
            fkiKey,
            mostRecentInactiveKey,
            "Found inactive resource deleted more recently than when active resource was created");
      }
    }
  }
}
