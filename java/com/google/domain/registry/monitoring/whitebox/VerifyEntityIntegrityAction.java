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

package com.google.domain.registry.monitoring.whitebox;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.domain.registry.model.EppResourceUtils.isActive;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static com.google.domain.registry.util.DateTimeUtils.earliestOf;
import static com.google.domain.registry.util.DateTimeUtils.isBeforeOrAt;
import static com.google.domain.registry.util.DateTimeUtils.latestOf;
import static com.google.domain.registry.util.FormattingLogger.getLoggerForCallerClass;
import static com.google.domain.registry.util.PipelineUtils.createJobPath;
import static com.googlecode.objectify.Key.getKind;
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
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.mapreduce.inputs.EppResourceInputs;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.domain.DomainBase;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.index.DomainApplicationIndex;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyContactIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyDomainIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyHostIndex;
import com.google.domain.registry.model.transfer.TransferData.TransferServerApproveEntity;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.Response;
import com.google.domain.registry.util.FormattingLogger;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;

import org.joda.time.DateTime;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * A mapreduce to verify integrity of entities in Datastore.
 *
 * <p>Specifically this validates all of the following system invariants that are expected to hold
 * true for all {@link EppResource} entities and their related indexes:
 * <ul>
 *   <li>All {@link Key} and {@link Ref} fields (including nested ones) point to entities that
 *       exist.
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
@Action(path = "/_dr/task/verifyEntityIntegrity")
public class VerifyEntityIntegrityAction implements Runnable {

  @VisibleForTesting
  static final FormattingLogger logger = getLoggerForCallerClass();
  private static final int NUM_SHARDS = 200;
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
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Verify entity integrity")
        .setModuleName("backend")
        .setDefaultReduceShards(NUM_SHARDS)
        .runMapreduce(
            new VerifyEntityIntegrityMapper(),
            new VerifyEntityIntegrityReducer(),
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

  private static enum EntityKind {
    DOMAIN,
    APPLICATION,
    CONTACT,
    HOST
  }

  private static class FkAndKind implements Serializable {

    private static final long serialVersionUID = -8466899721968889534L;

    public String foreignKey;
    public EntityKind kind;

    public static FkAndKind create(EntityKind kind, String foreignKey) {
      FkAndKind instance = new FkAndKind();
      instance.kind = kind;
      instance.foreignKey = foreignKey;
      return instance;
    }
  }

  /**
   * Mapper that checks validity of references on all resources and outputs key/value pairs used to
   * check integrity of foreign key entities.
   */
  public static class VerifyEntityIntegrityMapper
      extends Mapper<Object, FkAndKind, Key<? extends ImmutableObject>> {

    private static final long serialVersionUID = -8881987421971102016L;

    public VerifyEntityIntegrityMapper() {}

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
      } catch (Exception e) {
        // Log and swallow so that the mapreduce doesn't abort on first error.
        logger.severefmt(e, "Integrity error found while parsing entity: %s", keyOrEntity);
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
        throw new IllegalStateException(String.format("Unknown entity in mapper: %s", entity));
      }
    }

    private void mapEppResource(EppResource resource) {
      if (resource instanceof DomainBase) {
        DomainBase domainBase = (DomainBase) resource;
        Key<?> key = Key.create(domainBase);
        verifyExistence(key, bustUnions(domainBase.getReferencedContacts()));
        verifyExistence(key, bustUnions(domainBase.getNameservers()));
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
              FkAndKind.create(EntityKind.APPLICATION, application.getFullyQualifiedDomainName()),
              Key.create(application));
        } else if (domainBase instanceof DomainResource) {
          getContext().incrementCounter("domain resources");
          DomainResource domain = (DomainResource) domainBase;
          verifyExistence(key, domain.getApplication());
          verifyExistence(key, domain.getAutorenewBillingEvent());
          verifyExistence(key, domain.getAutorenewPollMessage());
          for (GracePeriod gracePeriod : domain.getGracePeriods()) {
            verifyExistence(key, gracePeriod.getOneTimeBillingEvent());
            verifyExistence(key, gracePeriod.getRecurringBillingEvent());
          }
          emit(
              FkAndKind.create(EntityKind.DOMAIN, domain.getFullyQualifiedDomainName()),
              Key.create(domain));
        }
      } else if (resource instanceof ContactResource) {
        getContext().incrementCounter("contact resources");
        ContactResource contact = (ContactResource) resource;
        emit(
            FkAndKind.create(EntityKind.CONTACT, contact.getContactId()),
            Key.create(contact));
      } else if (resource instanceof HostResource) {
        getContext().incrementCounter("host resources");
        HostResource host = (HostResource) resource;
        verifyExistence(Key.create(host), host.getSuperordinateDomain());
        emit(
            FkAndKind.create(EntityKind.HOST, host.getFullyQualifiedHostName()),
            Key.create(host));
      } else {
        throw new IllegalStateException(
            String.format("EppResource with unknown type: %s", resource));
      }
    }

    private void mapForeignKeyIndex(ForeignKeyIndex<?> fki) {
      @SuppressWarnings("cast")
      EppResource resource = verifyExistence(Key.create(fki), fki.getReference());
      checkState(
          fki.getForeignKey().equals(resource.getForeignKey()),
          "Foreign key index %s points to EppResource with different foreign key: %s",
          fki,
          resource);
      if (resource instanceof DomainResource) {
        getContext().incrementCounter("domain foreign key indexes");
        emit(FkAndKind.create(EntityKind.DOMAIN, resource.getForeignKey()), Key.create(fki));
      } else if (resource instanceof ContactResource) {
        getContext().incrementCounter("contact foreign key indexes");
        emit(FkAndKind.create(EntityKind.CONTACT, resource.getForeignKey()), Key.create(fki));
      } else if (resource instanceof HostResource) {
        getContext().incrementCounter("host foreign key indexes");
        emit(FkAndKind.create(EntityKind.HOST, resource.getForeignKey()), Key.create(fki));
      } else {
        throw new IllegalStateException(
            String.format(
                "Foreign key index %s points to EppResource of unknown type: %s", fki, resource));
      }
    }

    private void mapDomainApplicationIndex(DomainApplicationIndex dai) {
      getContext().incrementCounter("domain application indexes");
      for (Ref<DomainApplication> ref : dai.getReferences()) {
        DomainApplication application = verifyExistence(Key.create(dai), ref);
        checkState(
            dai.getFullyQualifiedDomainName().equals(application.getFullyQualifiedDomainName()),
            "Domain application index %s points to application with different domain name: %s",
            dai,
            application);
        emit(
            FkAndKind.create(EntityKind.APPLICATION, application.getFullyQualifiedDomainName()),
            Key.create(application));
      }
    }

    private void mapEppResourceIndex(EppResourceIndex eri) {
      @SuppressWarnings("cast")
      EppResource resource = verifyExistence(Key.create(eri), eri.getReference());
      if (resource instanceof DomainResource) {
        getContext().incrementCounter("domain EPP resource indexes");
        emit(FkAndKind.create(EntityKind.DOMAIN, resource.getForeignKey()), Key.create(eri));
      } else if (resource instanceof ContactResource) {
        getContext().incrementCounter("contact EPP resource indexes");
        emit(
            FkAndKind.create(EntityKind.CONTACT, resource.getForeignKey()), Key.create(eri));
      } else if (resource instanceof HostResource) {
        getContext().incrementCounter("host EPP resource indexes");
        emit(FkAndKind.create(EntityKind.HOST, resource.getForeignKey()), Key.create(eri));
      } else {
        throw new IllegalStateException(
            String.format(
                "EPP resource index %s points to resource of unknown type: %s", eri, resource));
      }
    }

    private static <E> void verifyExistence(Key<?> source, Set<Key<E>> keys) {
      Set<Key<E>> missingEntityKeys = Sets.difference(keys, ofy().load().<E>keys(keys).keySet());
      checkState(
          missingEntityKeys.isEmpty(),
          "Existing entity %s referenced entities that do not exist: %s",
          source,
          missingEntityKeys);
    }

    @Nullable
    private static <E> E verifyExistence(Key<?> source, @Nullable Ref<E> target) {
      if (target == null) {
        return null;
      }
      return verifyExistence(source, target.getKey());
    }

    @Nullable
    private static <E> E verifyExistence(Key<?> source, @Nullable Key<E> target) {
      if (target == null) {
        return null;
      }
      E entity = ofy().load().key(target).now();
      checkState(entity != null,
          "Existing entity %s referenced entity that does not exist: %s",
          source,
          target);
      return entity;
    }

    private static <E extends EppResource> Set<Key<E>> bustUnions(
        Iterable<ReferenceUnion<E>> unions) {
      return FluentIterable
          .from(unions)
          .transform(new Function<ReferenceUnion<E>, Key<E>>() {
            @Override
            public Key<E> apply(ReferenceUnion<E> union) {
              return union.getLinked().getKey();
            }})
          .toSet();
    }
  }

  /** Reducer that checks integrity of foreign key entities. */
  public static class VerifyEntityIntegrityReducer
      extends Reducer<FkAndKind, Key<? extends ImmutableObject>, Void> {

    private static final long serialVersionUID = -8531280188397051521L;

    @SuppressWarnings("unchecked")
    @Override
    public void reduce(FkAndKind fkAndKind, ReducerInput<Key<? extends ImmutableObject>> keys) {
      try {
        reduceKeys(fkAndKind, keys);
      } catch (Exception e) {
        // Log and swallow so that the mapreduce doesn't abort on first error.
        logger.severefmt(
            e, "Integrity error found while checking foreign key contraints for: %s", fkAndKind);
      }
    }

    private void reduceKeys(
        FkAndKind fkAndKind, ReducerInput<Key<? extends ImmutableObject>> keys) {
      switch (fkAndKind.kind) {
        case APPLICATION:
          getContext().incrementCounter("domain applications");
          checkIndexes(
              keys,
              fkAndKind.foreignKey,
              KIND_DOMAIN_BASE_RESOURCE,
              KIND_DOMAIN_APPLICATION_INDEX,
              false);
          break;
        case CONTACT:
          getContext().incrementCounter("contact resources");
          checkIndexes(keys, fkAndKind.foreignKey, KIND_CONTACT_RESOURCE, KIND_CONTACT_INDEX, true);
          break;
        case DOMAIN:
          getContext().incrementCounter("domain resources");
          checkIndexes(
              keys, fkAndKind.foreignKey, KIND_DOMAIN_BASE_RESOURCE, KIND_DOMAIN_INDEX, true);
          break;
        case HOST:
          getContext().incrementCounter("host resources");
          checkIndexes(keys, fkAndKind.foreignKey, KIND_HOST_RESOURCE, KIND_HOST_INDEX, true);
          break;
        default:
          throw new IllegalStateException(
              String.format("Unknown type of foreign key %s", fkAndKind.kind));
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
      List<Key<EppResourceIndex>> eppResourceIndexes = new ArrayList<>();
      while (keys.hasNext()) {
        Key<?> key = keys.next();
        if (key.getKind().equals(resourceKind)) {
          resources.add((Key<R>) key);
        } else if (key.getKind().equals(foreignKeyIndexKind)) {
          foreignKeyIndexes.add((Key<I>) key);
        } else if (key.getKind().equals(KIND_EPPRESOURCE_INDEX)) {
          eppResourceIndexes.add((Key<EppResourceIndex>) key);
        } else {
          throw new IllegalStateException(
              String.format(
                  "While processing links to foreign key %s of type %s, found unknown key: %s",
                  foreignKey,
                  resourceKind,
                  key));
        }
      }
      checkState(
          foreignKeyIndexes.size() == 1,
          String.format(
              "Should have found exactly 1 foreign key index for %s, instead found %d: %s",
              foreignKey,
              foreignKeyIndexes.size(),
              foreignKeyIndexes));
      checkState(
          !resources.isEmpty(),
          "Foreign key index %s exists, but no matching EPP resources found",
          getOnlyElement(foreignKeyIndexes));
      checkState(eppResourceIndexes.size() == 1,
          "Should have found exactly 1 EPP resource index for %s, instead found: %s",
          foreignKey,
          eppResourceIndexes);
      if (thereCanBeOnlyOne) {
        verifyOnlyOneActiveResource(foreignKey, resources, foreignKeyIndexes);
      }
    }

    private <R extends EppResource, I> void verifyOnlyOneActiveResource(
        String foreignKey, List<Key<R>> resources, List<Key<I>> foreignKeyIndexes) {
      DateTime now = DateTime.now(UTC);
      DateTime oldestActive = END_OF_TIME;
      DateTime mostRecentInactive = START_OF_TIME;
      List<R> activeResources = new ArrayList<R>();
      Collection<R> allResources = ofy().load().keys(resources).values();
      ForeignKeyIndex<?> fki =
          (ForeignKeyIndex<?>) ofy().load().key(getOnlyElement(foreignKeyIndexes)).now();
      for (R resource : allResources) {
        if (isActive(resource, now)) {
          activeResources.add(resource);
          oldestActive = earliestOf(oldestActive, resource.getCreationTime());
        } else {
          mostRecentInactive = latestOf(mostRecentInactive, resource.getDeletionTime());
        }
      }
      if (activeResources.isEmpty()) {
        checkState(
            fki.getDeletionTime().isEqual(mostRecentInactive),
            "Deletion time on foreign key index %s doesn't match"
                + " most recently deleted resource from: %s",
            fki,
            allResources);
      } else {
        checkState(
            activeResources.size() <= 1,
            "Found multiple active resources with foreign key %s: %s",
            foreignKey,
            activeResources);
        checkState(
            fki.getDeletionTime().isEqual(END_OF_TIME),
            "Deletion time on foreign key index %s doesn't match active resource: %s",
            fki,
            getOnlyElement(activeResources));
        checkState(
            isBeforeOrAt(mostRecentInactive, oldestActive),
            "Found inactive resource that is more recent than active resource in: %s",
            allResources);
      }

    }
  }
}
