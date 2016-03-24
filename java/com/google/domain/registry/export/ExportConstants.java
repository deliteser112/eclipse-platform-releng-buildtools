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

package com.google.domain.registry.export;

import static com.google.common.base.Predicates.not;
import static com.google.domain.registry.model.EntityClasses.CLASS_TO_KIND_FUNCTION;
import static com.google.domain.registry.util.TypeUtils.hasAnnotation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.domain.registry.model.EntityClasses;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.annotations.NotBackedUp;
import com.google.domain.registry.model.annotations.VirtualEntity;
import com.google.domain.registry.model.billing.BillingEvent.Cancellation;
import com.google.domain.registry.model.billing.BillingEvent.Modification;
import com.google.domain.registry.model.billing.BillingEvent.OneTime;
import com.google.domain.registry.model.billing.BillingEvent.Recurring;
import com.google.domain.registry.model.billing.RegistrarCredit;
import com.google.domain.registry.model.billing.RegistrarCreditBalance;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DomainBase;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.index.DomainApplicationIndex;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyContactIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyDomainIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyHostIndex;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.label.PremiumList;
import com.google.domain.registry.model.registry.label.PremiumList.PremiumListEntry;
import com.google.domain.registry.model.reporting.HistoryEntry;

/** Constants related to export code. */
public final class ExportConstants {

  /** Set of entity classes to export into BigQuery for reporting purposes. */
  @VisibleForTesting
  @SuppressWarnings("unchecked")  // varargs
  static final ImmutableSet<Class<? extends ImmutableObject>> REPORTING_ENTITY_CLASSES =
      ImmutableSet.of(
          Cancellation.class,
          ContactResource.class,
          DomainApplicationIndex.class,
          DomainBase.class,
          EppResourceIndex.class,
          ForeignKeyContactIndex.class,
          ForeignKeyDomainIndex.class,
          ForeignKeyHostIndex.class,
          HistoryEntry.class,
          HostResource.class,
          Modification.class,
          OneTime.class,
          PremiumList.class,
          PremiumListEntry.class,
          Recurring.class,
          Registrar.class,
          RegistrarContact.class,
          RegistrarCredit.class,
          RegistrarCreditBalance.class,
          Registry.class);

  /** Returns the names of kinds to include in datastore backups. */
  public static ImmutableSet<String> getBackupKinds() {
    // Back up all entity classes that aren't annotated with @VirtualEntity (never even persisted
    // to datastore, so they can't be backed up) or @NotBackedUp (intentionally omitted).
    return FluentIterable.from(EntityClasses.ALL_CLASSES)
        .filter(not(hasAnnotation(VirtualEntity.class)))
        .filter(not(hasAnnotation(NotBackedUp.class)))
        .transform(CLASS_TO_KIND_FUNCTION)
        .toSortedSet(Ordering.natural());
  }

  /** Returns the names of kinds to import into reporting tools (e.g. BigQuery). */
  public static ImmutableSet<String> getReportingKinds() {
    return FluentIterable.from(REPORTING_ENTITY_CLASSES)
        .transform(CLASS_TO_KIND_FUNCTION)
        .toSortedSet(Ordering.natural());
  }
}
