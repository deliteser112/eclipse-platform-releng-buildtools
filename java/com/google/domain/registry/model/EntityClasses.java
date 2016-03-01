// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.model;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.billing.RegistrarBillingEntry;
import com.google.domain.registry.model.billing.RegistrarCredit;
import com.google.domain.registry.model.billing.RegistrarCreditBalance;
import com.google.domain.registry.model.common.EntityGroupRoot;
import com.google.domain.registry.model.common.GaeUserIdConverter;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.domain.DomainBase;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.export.LogsExportCursor;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.index.DomainApplicationIndex;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.EppResourceIndexBucket;
import com.google.domain.registry.model.index.ForeignKeyIndex;
import com.google.domain.registry.model.ofy.CommitLogBucket;
import com.google.domain.registry.model.ofy.CommitLogCheckpoint;
import com.google.domain.registry.model.ofy.CommitLogCheckpointRoot;
import com.google.domain.registry.model.ofy.CommitLogManifest;
import com.google.domain.registry.model.ofy.CommitLogMutation;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.rde.RdeRevision;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.label.PremiumList;
import com.google.domain.registry.model.registry.label.ReservedList;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.model.server.Lock;
import com.google.domain.registry.model.server.ServerSecret;
import com.google.domain.registry.model.smd.SignedMarkRevocationList;
import com.google.domain.registry.model.tmch.ClaimsListShard;
import com.google.domain.registry.model.tmch.ClaimsListShard.ClaimsListRevision;
import com.google.domain.registry.model.tmch.ClaimsListShard.ClaimsListSingleton;
import com.google.domain.registry.model.tmch.TmchCrl;

import com.googlecode.objectify.Key;

/** Sets of classes of the Objectify-registered entities in use throughout the model. */
public final class EntityClasses {

  /** Set of entity classes. */
  @SuppressWarnings("unchecked")  // varargs
  public static final ImmutableSet<Class<? extends ImmutableObject>> ALL_CLASSES =
      ImmutableSet.<Class<? extends ImmutableObject>>of(
          BillingEvent.Cancellation.class,
          BillingEvent.Modification.class,
          BillingEvent.OneTime.class,
          BillingEvent.Recurring.class,
          ClaimsListShard.class,
          ClaimsListRevision.class,
          ClaimsListSingleton.class,
          CommitLogBucket.class,
          CommitLogCheckpoint.class,
          CommitLogCheckpointRoot.class,
          CommitLogManifest.class,
          CommitLogMutation.class,
          ContactResource.class,
          DomainApplication.class,
          DomainApplicationIndex.class,
          DomainBase.class,
          DomainResource.class,
          EntityGroupRoot.class,
          EppResourceIndex.class,
          EppResourceIndexBucket.class,
          ForeignKeyIndex.ForeignKeyContactIndex.class,
          ForeignKeyIndex.ForeignKeyDomainIndex.class,
          ForeignKeyIndex.ForeignKeyHostIndex.class,
          GaeUserIdConverter.class,
          HistoryEntry.class,
          HostResource.class,
          Lock.class,
          LogsExportCursor.class,
          PollMessage.class,
          PollMessage.Autorenew.class,
          PollMessage.OneTime.class,
          PremiumList.class,
          PremiumList.PremiumListEntry.class,
          PremiumList.PremiumListRevision.class,
          RdeRevision.class,
          Registrar.class,
          RegistrarBillingEntry.class,
          RegistrarContact.class,
          RegistrarCredit.class,
          RegistrarCreditBalance.class,
          Registry.class,
          RegistryCursor.class,
          ReservedList.class,
          ServerSecret.class,
          SignedMarkRevocationList.class,
          TmchCrl.class);

  /**
   * Function that converts an Objectify-registered class to its datastore kind name.
   *
   * <p>Note that this mapping is not one-to-one, since polymorphic subclasses of an entity all
   * have the same datastore kind.  (In theory, two distinct top-level entities could also map to
   * the same kind since it's just {@code class.getSimpleName()}, but we test against that.)
   */
  public static final Function<Class<? extends ImmutableObject>, String> CLASS_TO_KIND_FUNCTION =
      new Function<Class<? extends ImmutableObject>, String>() {
        @Override
        public String apply(Class<? extends ImmutableObject> clazz) {
          return Key.getKind(clazz);
        }
      };

  private EntityClasses() {}
}
